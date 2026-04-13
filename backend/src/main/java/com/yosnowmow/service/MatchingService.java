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
 * Matches available Workers to a newly-created job.
 *
 * Called {@code @Async} from {@code JobController} immediately after a job is saved,
 * so the POST /api/jobs response returns before matching completes.  The method
 * stores the ordered candidate list on the job document and then hands off to
 * {@link DispatchService} to begin the sequential 10-minute offer round.
 *
 * <h3>Matching criteria (Phase 1)</h3>
 * <ol>
 *   <li>{@code worker.status == "available"} (Firestore query filter)</li>
 *   <li>{@code worker.baseCoords} is non-null — geocoded address exists</li>
 *   <li>Haversine distance from job property ≤ {@code worker.serviceRadiusKm}
 *       (or + {@value #BUFFER_FRACTION_PCT}% if {@code worker.bufferOptIn})</li>
 *   <li>If {@code job.personalWorkerOnly}: {@code worker.designation == "personal"}</li>
 *   <li>{@code worker.activeJobCount < worker.capacityMax} — not at capacity</li>
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

    private final Firestore firestore;
    private final DispatchService dispatchService;

    public MatchingService(Firestore firestore, DispatchService dispatchService) {
        this.firestore = firestore;
        this.dispatchService = dispatchService;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Finds eligible Workers for the given job, stores the ordered candidate list
     * on the job document, and triggers the first dispatch offer.
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

            // Hand off to dispatch — this starts the first 10-minute offer window.
            dispatchService.dispatchToNextWorker(jobId);

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

            // Respect capacityMax (always 1 in Phase 1).
            if (worker.getActiveJobCount() >= worker.getCapacityMax()) continue;

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
