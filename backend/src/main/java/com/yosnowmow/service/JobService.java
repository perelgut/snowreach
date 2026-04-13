package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.dto.CreateJobRequest;
import com.yosnowmow.exception.JobNotFoundException;
import com.yosnowmow.model.Address;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Business logic for job lifecycle management.
 *
 * P1-08 scope: job creation (REQUESTED state) and basic reads.
 * Later tasks add transitions:
 *   - P1-09 / P1-10: matching and dispatch (REQUESTED → offers sent)
 *   - P1-11: Stripe escrow (REQUESTED → PENDING_DEPOSIT → CONFIRMED)
 *   - P1-13: full state machine transitions
 *   - P1-14: cancellation with fee logic
 *
 * Architecture rule: AuditLogService.write() is called BEFORE every
 * state-changing Firestore write.
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private static final String JOBS_COLLECTION = "jobs";

    /** Job statuses considered "active" — a Requester may only have one at a time. */
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "REQUESTED", "PENDING_DEPOSIT", "CONFIRMED", "IN_PROGRESS"
    );

    /** Valid scope values per spec §3.2. */
    private static final Set<String> VALID_SCOPE = Set.of("driveway", "sidewalk", "both");

    private final Firestore firestore;
    private final UserService userService;
    private final GeocodingService geocodingService;
    private final AuditLogService auditLogService;

    public JobService(Firestore firestore,
                      UserService userService,
                      GeocodingService geocodingService,
                      AuditLogService auditLogService) {
        this.firestore = firestore;
        this.userService = userService;
        this.geocodingService = geocodingService;
        this.auditLogService = auditLogService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a new job in the {@code REQUESTED} state.
     *
     * Steps:
     * 1. Confirm the Requester has a registered user document.
     * 2. Guard: Requester must not already have an active job.
     * 3. Validate scope values.
     * 4. Geocode the property address (fails fast if address is unresolvable).
     * 5. Build Job document with all nullable pricing fields set to null.
     * 6. Write audit entry (before Firestore write).
     * 7. Write to {@code jobs/{jobId}}.
     * 8. Note: dispatch (P1-09/P1-10) is triggered by the controller after return.
     *
     * @param caller the authenticated Requester
     * @param req    the job posting payload
     * @return the newly created Job document
     */
    public Job createJob(AuthenticatedUser caller, CreateJobRequest req) {
        String requesterId = caller.uid();

        // 1. Confirm user profile exists
        User requester = userService.getUser(requesterId);

        // 2. Guard: no active job already
        guardNoActiveJob(requesterId);

        // 3. Validate scope
        validateScope(req.getScope());

        // 4. Geocode property address
        Address propertyAddress = new Address(req.getPropertyAddressText());
        GeocodingService.GeocodeResult geo;
        try {
            geo = geocodingService.geocode(req.getPropertyAddressText());
        } catch (GeocodingService.GeocodingException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not resolve the property address. Please check the address and try again.");
        }
        propertyAddress.setFullText(req.getPropertyAddressText());

        // 5. Build Job
        String jobId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.now();

        Job job = new Job();
        job.setJobId(jobId);
        job.setRequesterId(requesterId);
        job.setStatus("REQUESTED");
        job.setScope(req.getScope());
        job.setPropertyAddress(propertyAddress);
        job.setPropertyCoords(geo.coords());
        job.setPersonalWorkerOnly(req.isPersonalWorkerOnly());
        job.setNotesForWorker(req.getNotesForWorker());
        job.setRequestImageIds(req.getRequestImageIds() != null
                ? req.getRequestImageIds() : Collections.emptyList());
        job.setCompletionImageIds(Collections.emptyList());
        job.setDisputeImageIds(Collections.emptyList());
        job.setContactedWorkerIds(new ArrayList<>());
        job.setSimultaneousOfferWorkerIds(new ArrayList<>());
        job.setSelectedWorkerIds(req.getSelectedWorkerIds());
        job.setOfferRound(0);
        job.setCannotCompleteCountThisJob(0);
        job.setRequestedAt(now);
        job.setCreatedAt(now);
        job.setUpdatedAt(now);

        if (req.getStartWindowEarliest() != null) {
            job.setStartWindowEarliest(instantToTimestamp(req.getStartWindowEarliest()));
        }
        if (req.getStartWindowLatest() != null) {
            job.setStartWindowLatest(instantToTimestamp(req.getStartWindowLatest()));
        }

        // 6. Audit BEFORE write
        auditLogService.write(requesterId, "JOB_CREATED", "job", jobId, null, job);

        // 7. Write to Firestore
        writeJob(job);

        log.info("Job created: jobId={} requesterId={} scope={}", jobId, requesterId, req.getScope());
        return job;
    }

    /**
     * Returns a job by ID.
     *
     * Access control is enforced in the controller — this method always
     * returns the full document.
     *
     * @throws JobNotFoundException if no document exists for this ID
     */
    public Job getJob(String jobId) {
        try {
            DocumentSnapshot snap = firestore.collection(JOBS_COLLECTION)
                    .document(jobId).get().get();
            if (!snap.exists()) {
                throw new JobNotFoundException(jobId);
            }
            Job job = snap.toObject(Job.class);
            if (job == null) throw new JobNotFoundException(jobId);
            return job;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error fetching job {}: {}", jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch job");
        }
    }

    /**
     * Lists jobs filtered by the given criteria.
     *
     * Used by the admin endpoint (GET /api/jobs with optional query params).
     * Results are ordered by createdAt descending, limited to {@code limit} entries.
     *
     * @param statusFilter   optional status to filter by; null = all statuses
     * @param requesterIdFilter optional requester UID; null = all requesters
     * @param workerIdFilter    optional worker UID; null = all workers
     * @param limit          max results to return (1–100; defaults to 20)
     */
    public List<Job> listJobs(String statusFilter,
                               String requesterIdFilter,
                               String workerIdFilter,
                               int limit) {
        try {
            Query query = firestore.collection(JOBS_COLLECTION)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(Math.min(Math.max(limit, 1), 100));

            if (statusFilter != null)      query = query.whereEqualTo("status",      statusFilter);
            if (requesterIdFilter != null) query = query.whereEqualTo("requesterId", requesterIdFilter);
            if (workerIdFilter != null)    query = query.whereEqualTo("workerId",    workerIdFilter);

            QuerySnapshot snap = query.get().get();
            return snap.getDocuments().stream()
                    .map(doc -> doc.toObject(Job.class))
                    .collect(Collectors.toList());

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error listing jobs: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to list jobs");
        }
    }

    /**
     * Returns all non-terminal jobs for a given user (as Requester or Worker).
     * Used by the user-facing GET /api/jobs endpoint.
     */
    public List<Job> listJobsForUser(String userId) {
        try {
            // Fetch as Requester
            QuerySnapshot asRequester = firestore.collection(JOBS_COLLECTION)
                    .whereEqualTo("requesterId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().get();

            // Fetch as Worker
            QuerySnapshot asWorker = firestore.collection(JOBS_COLLECTION)
                    .whereEqualTo("workerId", userId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get().get();

            List<Job> jobs = new ArrayList<>();
            asRequester.getDocuments().forEach(d -> jobs.add(d.toObject(Job.class)));
            asWorker.getDocuments().forEach(d -> {
                Job j = d.toObject(Job.class);
                // Avoid duplicates for users who are both requester and worker on same job
                if (jobs.stream().noneMatch(existing -> existing.getJobId().equals(j.getJobId()))) {
                    jobs.add(j);
                }
            });

            jobs.sort((a, b) -> {
                if (a.getCreatedAt() == null) return 1;
                if (b.getCreatedAt() == null) return -1;
                return b.getCreatedAt().compareTo(a.getCreatedAt());
            });

            return jobs;

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error listing jobs for user {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to list jobs");
        }
    }

    // ── Package-private — used by other services ──────────────────────────────

    /**
     * Stores a list of Worker IDs onto the job as the current simultaneous offer round.
     * Called by DispatchService (P1-10) when an offer round is launched.
     */
    public void setOfferRound(String jobId, List<String> workerIds, Timestamp offerExpiry) {
        try {
            Job job = getJob(jobId);
            int nextRound = job.getOfferRound() + 1;

            java.util.Map<String, Object> auditAfter = new java.util.HashMap<>();
            auditAfter.put("offerRound", nextRound);
            auditAfter.put("workerIds",  workerIds);
            auditLogService.write("system", "OFFER_ROUND_STARTED", "job", jobId, job, auditAfter);

            firestore.collection(JOBS_COLLECTION).document(jobId).update(
                    "simultaneousOfferWorkerIds", workerIds,
                    "offerRound",                 nextRound,
                    "offerExpiry",                offerExpiry,
                    "offeredAt",                  Timestamp.now(),
                    "updatedAt",                  Timestamp.now()
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update offer round");
        }
    }

    /**
     * Appends a Worker ID to the job's contactedWorkerIds list.
     * Called by DispatchService when a Worker declines or times out.
     */
    public void markWorkerContacted(String jobId, String workerId) {
        try {
            firestore.collection(JOBS_COLLECTION).document(jobId).update(
                    "contactedWorkerIds", com.google.cloud.firestore.FieldValue.arrayUnion(workerId),
                    "updatedAt",          Timestamp.now()
            ).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to mark worker {} as contacted on job {}", workerId, jobId);
        }
    }

    /**
     * Retrieves a job and validates the caller is allowed to see it.
     * Requester sees own jobs; Worker sees jobs they are assigned to; Admin sees all.
     */
    public Job getJobForCaller(String jobId, AuthenticatedUser caller) {
        Job job = getJob(jobId);
        boolean isRequester = job.getRequesterId().equals(caller.uid());
        boolean isWorker    = caller.uid().equals(job.getWorkerId());
        boolean isAdmin     = caller.hasRole("admin");

        if (!isRequester && !isWorker && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have access to this job");
        }
        return job;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Throws HTTP 409 if the Requester already has a job in an active state.
     * A Requester may only have one active job at a time (Phase 1).
     */
    private void guardNoActiveJob(String requesterId) {
        try {
            QuerySnapshot snap = firestore.collection(JOBS_COLLECTION)
                    .whereEqualTo("requesterId", requesterId)
                    .whereIn("status", new ArrayList<>(ACTIVE_STATUSES))
                    .limit(1)
                    .get().get();

            if (!snap.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You already have an active job. Complete or cancel it before posting a new one.");
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error checking active jobs for {}: {}", requesterId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to check existing jobs");
        }
    }

    /** Validates all scope values are in the allowed set. */
    private void validateScope(List<String> scope) {
        for (String s : scope) {
            if (!VALID_SCOPE.contains(s)) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Invalid scope value: '" + s + "'. Must be one of: driveway, sidewalk, both.");
            }
        }
    }

    /** Writes the job document to Firestore, wrapping checked exceptions. */
    private void writeJob(Job job) {
        try {
            firestore.collection(JOBS_COLLECTION)
                     .document(job.getJobId())
                     .set(job)
                     .get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error writing job {}: {}", job.getJobId(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create job");
        }
    }

    /** Converts a Java {@link Instant} to a Firestore {@link Timestamp}. */
    private Timestamp instantToTimestamp(Instant instant) {
        return Timestamp.ofTimeSecondsAndNanos(instant.getEpochSecond(), instant.getNano());
    }

}
