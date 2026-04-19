package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import com.yosnowmow.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Matches available Workers to a newly-posted job.
 *
 * Called {@code @Async} from {@code JobController} immediately after a job is saved,
 * so the POST /api/jobs response returns before matching completes.  The method
 * stores the ordered candidate list on the job document and then notifies the top 3
 * via push — all in-range Workers can still discover the job themselves.
 *
 * <h3>Matching criteria</h3>
 * <ol>
 *   <li>{@code worker.status == "available"} (Firestore query filter)</li>
 *   <li>{@code worker.baseCoords} is non-null — geocoded address exists</li>
 *   <li>Haversine distance from job property ≤ {@code worker.serviceRadiusKm}
 *       (or + {@value #BUFFER_FRACTION_PCT}% if {@code worker.bufferOptIn})</li>
 *   <li>If {@code job.personalWorkerOnly}: {@code worker.designation == "personal"}</li>
 *   <li>Live job count (ESCROW_HELD or IN_PROGRESS) {@code < worker.capacityMax} — not at capacity</li>
 * </ol>
 *
 * <h3>Sort order</h3>
 * Rating DESC (null treated as −1.0 to rank last), then distance ASC.
 * Maximum {@value #MAX_CANDIDATES} candidates stored in {@code matchedWorkerIds}.
 *
 * <h3>Selected-worker bypass</h3>
 * If {@code job.selectedWorkerIds} is non-empty the algorithm is skipped entirely —
 * those UIDs are used as-is in the order the Requester specified.
 */
@Service
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private static final String USERS_COLLECTION = "users";
    private static final String JOBS_COLLECTION  = "jobs";

    /** Extra radius fraction granted when {@code worker.bufferOptIn == true}. */
    private static final double BUFFER_FRACTION_PCT = 0.10;

    /** Maximum number of candidates stored in {@code matchedWorkerIds}. */
    private static final int MAX_CANDIDATES = 20;

    /** Top N workers notified by push when a job is posted. */
    private static final int TOP_NOTIFY_COUNT = 3;

    private final Firestore firestore;
    private final NotificationService notificationService;

    public MatchingService(Firestore firestore, NotificationService notificationService) {
        this.firestore           = firestore;
        this.notificationService = notificationService;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Finds eligible Workers for the given job, stores the ordered candidate list
     * on the job document, and sends push notifications to the top {@value #TOP_NOTIFY_COUNT}.
     *
     * All in-range Workers can discover the job on their own; the push nudge just
     * gives the best-ranked Workers a head start.
     *
     * Executes on a background thread (Spring async executor) — does not block the
     * POST /api/jobs HTTP response.  Failures are logged but not propagated.
     *
     * @param jobId Firestore document ID of the job to match
     */
    @Async
    public void matchAndStoreWorkers(String jobId) {
        log.info("Starting worker matching for job {}", jobId);
        try {
            Job job = fetchJob(jobId);
            if (job == null) {
                log.error("Match aborted — job {} not found in Firestore", jobId);
                return;
            }

            List<String> candidateUids;

            if (job.getSelectedWorkerIds() != null && !job.getSelectedWorkerIds().isEmpty()) {
                // Requester pre-selected specific Workers — bypass the algorithm.
                candidateUids = new ArrayList<>(job.getSelectedWorkerIds());
                log.info("Job {} uses pre-selected workers: {}", jobId, candidateUids);
            } else {
                candidateUids = runMatchingAlgorithm(job);
                log.info("Job {} — matched {} candidate(s)", jobId, candidateUids.size());
            }

            // Persist the ordered candidate list on the job document.
            firestore.collection(JOBS_COLLECTION).document(jobId).update(
                    "matchedWorkerIds", candidateUids,
                    "updatedAt",        Timestamp.now()
            ).get();

            // Notify the top workers; everyone else can discover the job in the marketplace.
            List<String> toNotify = candidateUids.stream().limit(TOP_NOTIFY_COUNT).toList();
            int postedCents = job.getPostedPriceCents() != null ? job.getPostedPriceCents() : 0;
            for (String workerUid : toNotify) {
                notificationService.notifyWorkerNewJobPosted(workerUid, jobId, postedCents);
            }

            log.info("Job {} — notified {} top worker(s)", jobId, toNotify.size());

        } catch (Exception e) {
            log.error("Worker matching failed for job {}: {}", jobId, e.getMessage(), e);
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Runs the candidate selection and ranking algorithm.
     *
     * @param job the job document (already fetched from Firestore)
     * @return ordered list of up to {@value #MAX_CANDIDATES} Worker UIDs
     */
    private List<String> runMatchingAlgorithm(Job job)
            throws InterruptedException, ExecutionException {

        if (job.getPropertyCoords() == null) {
            log.warn("Job {} has no propertyCoords — cannot compute distances", job.getJobId());
            return Collections.emptyList();
        }

        double jobLat = job.getPropertyCoords().getLatitude();
        double jobLon = job.getPropertyCoords().getLongitude();

        // Firestore dot-notation filters on the embedded worker sub-object.
        // Only users with worker.status == "available" are returned.
        QuerySnapshot snap = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("worker.status", "available")
                .get().get();

        List<WorkerCandidate> candidates = new ArrayList<>();

        for (QueryDocumentSnapshot doc : snap.getDocuments()) {
            User user = doc.toObject(User.class);
            if (user == null || user.getWorker() == null) continue;

            WorkerProfile worker = user.getWorker();

            // Need geocoded coordinates to compute distance.
            if (worker.getBaseCoords() == null) continue;

            // Live count of CONFIRMED or IN_PROGRESS jobs for this worker (P2-05).
            // This replaces the cached activeJobCount field, which can lag under
            // rapid state changes; a live query prevents over-dispatching.
            long liveActiveJobCount = countActiveJobsForWorker(doc.getId());
            if (liveActiveJobCount >= worker.getCapacityMax()) continue;

            double distance = GeoUtils.haversineDistanceKm(
                    jobLat, jobLon,
                    worker.getBaseCoords().getLatitude(),
                    worker.getBaseCoords().getLongitude());

            double effectiveRadius = worker.isBufferOptIn()
                    ? worker.getServiceRadiusKm() * (1.0 + BUFFER_FRACTION_PCT)
                    : worker.getServiceRadiusKm();

            if (distance > effectiveRadius) continue;

            // Honour personalWorkerOnly flag.
            if (job.isPersonalWorkerOnly()
                    && !"personal".equalsIgnoreCase(worker.getDesignation())) continue;

            candidates.add(new WorkerCandidate(doc.getId(), worker.getRating(), distance));
        }

        // Sort: highest rating first; ties broken by closest distance.
        // Null rating (< 10 jobs) is treated as −1.0 so those workers go last.
        candidates.sort(Comparator
                .comparingDouble((WorkerCandidate c) -> c.rating == null ? -1.0 : -c.rating)
                .thenComparingDouble(c -> c.distanceKm));

        return candidates.stream()
                .limit(MAX_CANDIDATES)
                .map(c -> c.uid)
                .collect(Collectors.toList());
    }

    /** Fetches a job document from Firestore; returns null if not found. */
    private Job fetchJob(String jobId) throws InterruptedException, ExecutionException {
        var snap = firestore.collection(JOBS_COLLECTION).document(jobId).get().get();
        return snap.exists() ? snap.toObject(Job.class) : null;
    }

    /**
     * Returns a live count of CONFIRMED or IN_PROGRESS jobs assigned to the
     * given Worker UID (P2-05).
     *
     * <p>This live query is used instead of the cached {@code worker.activeJobCount}
     * field to avoid stale data allowing a Worker to exceed their concurrent capacity
     * limit during rapid back-to-back job dispatches.
     *
     * <p>Requires a Firestore composite index on {@code jobs(workerId, status)}.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @return number of CONFIRMED or IN_PROGRESS jobs currently assigned to this Worker
     */
    private long countActiveJobsForWorker(String workerUid)
            throws InterruptedException, ExecutionException {

        return firestore.collection(JOBS_COLLECTION)
                .whereEqualTo("workerId", workerUid)
                .whereIn("status", List.of("ESCROW_HELD", "IN_PROGRESS"))
                .get()
                .get()
                .size();
    }

    /** Lightweight value object used during candidate ranking. */
    private static final class WorkerCandidate {
        final String uid;
        final Double rating;
        final double distanceKm;

        WorkerCandidate(String uid, Double rating, double distanceKm) {
            this.uid = uid;
            this.rating = rating;
            this.distanceKm = distanceKm;
        }
    }
}
