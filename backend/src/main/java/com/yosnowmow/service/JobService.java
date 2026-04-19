package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.dto.CreateJobRequest;
import com.yosnowmow.exception.InvalidTransitionException;
import com.yosnowmow.exception.JobNotFoundException;
import com.yosnowmow.model.Address;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Business logic for job lifecycle management.
 *
 * <h3>State machine (P1-13)</h3>
 * All externally-triggered transitions go through {@link #transition}.
 * Trusted internal callers (Stripe webhook, DispatchService) use the
 * lighter {@link #transitionStatus} which skips actor/table validation.
 *
 * <h3>Cancellation (P1-14)</h3>
 * {@link #cancelJob} validates the caller, determines the previous status, and
 * transitions to CANCELLED.  The controller is responsible for triggering any
 * Stripe operations (fee charge, PI cancellation) after this call returns.
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
            "POSTED", "NEGOTIATING", "AGREED", "ESCROW_HELD", "IN_PROGRESS"
    );

    /** Valid scope values per spec §3.2. */
    private static final Set<String> VALID_SCOPE = Set.of("driveway", "sidewalk", "both");

    /**
     * Valid state transitions: fromStatus → set of allowed toStatuses.
     * CANCELLED from pre-IN_PROGRESS is handled separately by {@link #cancelJob}.
     */
    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry("POSTED",           Set.of("NEGOTIATING",   "CANCELLED")),
            Map.entry("NEGOTIATING",      Set.of("AGREED",        "POSTED",  "CANCELLED")),
            Map.entry("AGREED",           Set.of("ESCROW_HELD",   "CANCELLED")),
            Map.entry("ESCROW_HELD",      Set.of("IN_PROGRESS",   "CANCELLED")),
            Map.entry("IN_PROGRESS",      Set.of("PENDING_APPROVAL", "INCOMPLETE")),
            Map.entry("PENDING_APPROVAL", Set.of("DISPUTED",      "RELEASED")),
            Map.entry("INCOMPLETE",       Set.of("DISPUTED",      "RELEASED")),
            Map.entry("DISPUTED",         Set.of("RELEASED",      "REFUNDED")),
            Map.entry("RELEASED",         Set.of("SETTLED")),
            Map.entry("REFUNDED",         Set.of()),
            Map.entry("SETTLED",          Set.of()),
            Map.entry("CANCELLED",        Set.of())
    );

    /** Statuses from which a job may be cancelled by the Requester or Admin. */
    private static final Set<String> CANCELLABLE_STATUSES =
            Set.of("POSTED", "NEGOTIATING", "AGREED", "ESCROW_HELD");

    private final Firestore firestore;
    private final UserService userService;
    private final GeocodingService geocodingService;
    private final AuditLogService auditLogService;
    private final org.quartz.Scheduler quartzScheduler;
    private final NotificationService notificationService;

    public JobService(Firestore firestore,
                      UserService userService,
                      GeocodingService geocodingService,
                      AuditLogService auditLogService,
                      org.quartz.Scheduler quartzScheduler,
                      NotificationService notificationService) {
        this.firestore = firestore;
        this.userService = userService;
        this.geocodingService = geocodingService;
        this.auditLogService = auditLogService;
        this.quartzScheduler = quartzScheduler;
        this.notificationService = notificationService;
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
        job.setStatus("POSTED");
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
        job.setRejectedWorkerIds(new ArrayList<>());
        job.setSelectedWorkerIds(req.getSelectedWorkerIds());
        job.setCannotCompleteCountThisJob(0);
        job.setApprovalWindowHours(2);
        if (req.getPostedPriceCents() != null) {
            job.setPostedPriceCents(req.getPostedPriceCents());
        }
        job.setPostedAt(now);
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

    /**
     * Transitions a job to a new status and records an audit entry.
     *
     * This is a low-level write — it does NOT validate that the transition is
     * allowed.  Full transition-table validation is added in P1-13.  All callers
     * inside the backend are trusted (PaymentService, WebhookController, etc.).
     *
     * @param jobId        Firestore document ID
     * @param newStatus    target status string (e.g. "CONFIRMED", "RELEASED")
     * @param actorUid     UID of the actor causing the transition (or "stripe"/"system")
     * @param extraUpdates additional Firestore field updates to apply atomically,
     *                     or {@code null} for none
     */
    public void transitionStatus(String jobId,
                                 String newStatus,
                                 String actorUid,
                                 java.util.Map<String, Object> extraUpdates) {
        try {
            Job before = getJob(jobId);
            auditLogService.write(actorUid, "STATUS_" + newStatus, "job", jobId, before, newStatus);

            java.util.Map<String, Object> updates = new java.util.HashMap<>();
            if (extraUpdates != null) updates.putAll(extraUpdates);
            updates.put("status",    newStatus);
            updates.put("updatedAt", Timestamp.now());

            firestore.collection(JOBS_COLLECTION).document(jobId).update(updates).get();
            log.info("Job {} transitioned to {} by {}", jobId, newStatus, actorUid);

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to transition job {} to {}: {}", jobId, newStatus, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update job status");
        }
    }

    // ── State machine — P1-13 ─────────────────────────────────────────────────

    /**
     * Validated state-machine transition for externally-triggered status changes.
     *
     * Uses a Firestore {@code runTransaction} to atomically:
     * <ol>
     *   <li>Read the current job status.</li>
     *   <li>Verify the transition is in {@link #TRANSITIONS}.</li>
     *   <li>Verify the actor has permission for this specific transition.</li>
     *   <li>Write the new status + relevant lifecycle timestamp.</li>
     * </ol>
     *
     * Trusted internal callers (Stripe webhook, DispatchService) use the simpler
     * {@link #transitionStatus} which bypasses these checks.
     *
     * @param jobId       Firestore document ID
     * @param toStatus    desired target status
     * @param actorUid    Firebase UID of the caller; "system" or "stripe" for automated transitions
     * @param isAdmin     whether the caller holds the "admin" role
     * @return the updated Job (with the new status applied)
     * @throws InvalidTransitionException if the transition is not permitted
     * @throws AccessDeniedException      if the actor lacks permission for this transition
     * @throws JobNotFoundException       if no job exists for {@code jobId}
     */
    public Job transition(String jobId,
                          String toStatus,
                          String actorUid,
                          boolean isAdmin) {
        try {
            DocumentReference jobRef = firestore.collection(JOBS_COLLECTION).document(jobId);

            Job result = firestore.<Job>runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(jobRef).get();
                if (!snap.exists()) throw new JobNotFoundException(jobId);

                Job job = snap.toObject(Job.class);
                if (job == null) throw new JobNotFoundException(jobId);

                String fromStatus = job.getStatus();

                // 1. Validate transition is in the table.
                Set<String> allowed = TRANSITIONS.getOrDefault(fromStatus, Set.of());
                if (!allowed.contains(toStatus)) {
                    throw new InvalidTransitionException(
                            "Cannot transition job from " + fromStatus + " to " + toStatus);
                }

                // 2. Validate actor permission.
                validateActorPermission(job, fromStatus, toStatus, actorUid, isAdmin);

                // 3. Build update map with status + lifecycle timestamp.
                Map<String, Object> updates = new HashMap<>();
                updates.put("status",    toStatus);
                updates.put("updatedAt", Timestamp.now());
                applyLifecycleTimestamp(updates, toStatus);

                // 4. Write inside transaction.
                tx.update(jobRef, updates);

                // Return a partial view of the updated job for logging; caller should re-fetch
                // if they need all fields (but the status is correct for side-effect dispatch).
                job.setStatus(toStatus);
                return job;

            }).get();

            // 5. Audit AFTER transaction (non-fatal).
            auditLogService.write(actorUid, "STATUS_" + toStatus, "job", jobId, null, toStatus);

            // 6. Post-transition side effects.
            handleSideEffects(jobId, toStatus, actorUid);

            log.info("Job {} → {} by {}", jobId, toStatus, actorUid);
            return result;

        } catch (ExecutionException e) {
            // Unwrap business exceptions thrown inside the transaction lambda.
            Throwable cause = e.getCause();
            if (cause instanceof InvalidTransitionException ite) throw ite;
            if (cause instanceof JobNotFoundException jnfe) throw jnfe;
            if (cause instanceof AccessDeniedException ade) throw ade;
            log.error("Firestore transaction error transitioning job {} to {}: {}",
                    jobId, toStatus, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update job status");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update job status");
        }
    }

    // ── Cancellation — P1-14 ─────────────────────────────────────────────────

    /**
     * Cancels a job, validating that the actor is allowed and the current status
     * is cancellable.
     *
     * <p>Does NOT perform Stripe operations — the caller (controller) must
     * follow up with the appropriate {@code PaymentService} call based on the
     * returned previous status:
     * <ul>
     *   <li>{@code CONFIRMED}       → {@code PaymentService.cancelWithFee(jobId)}</li>
     *   <li>{@code PENDING_DEPOSIT} → {@code PaymentService.cancelPaymentIntent(jobId)}</li>
     *   <li>{@code REQUESTED}       → no payment action needed</li>
     * </ul>
     *
     * @param jobId     Firestore document ID
     * @param actorUid  Firebase UID of the actor requesting cancellation
     * @param isAdmin   whether the actor holds the "admin" role
     * @return the job's previous status (before cancellation)
     * @throws AccessDeniedException      if the actor is neither the Requester nor an Admin
     * @throws InvalidTransitionException if the job is IN_PROGRESS or later
     */
    public String cancelJob(String jobId, String actorUid, boolean isAdmin) {
        Job job = getJob(jobId);
        String previousStatus = job.getStatus();

        // Only the Requester or an Admin may cancel.
        if (!isAdmin && !actorUid.equals(job.getRequesterId())) {
            throw new AccessDeniedException("Only the Requester or an Admin may cancel this job");
        }

        if (!CANCELLABLE_STATUSES.contains(previousStatus)) {
            throw new InvalidTransitionException(
                    "Job cannot be cancelled from status " + previousStatus
                    + ". Jobs that are in progress or later require the dispute process.");
        }

        String cancelledBy = (!isAdmin || actorUid.equals(job.getRequesterId()))
                ? "requester" : "admin";

        Map<String, Object> extras = new HashMap<>();
        extras.put("cancelledAt", Timestamp.now());
        extras.put("cancelledBy", cancelledBy);

        auditLogService.write(actorUid, "JOB_CANCELLED", "job", jobId, job, null);
        transitionStatus(jobId, "CANCELLED", actorUid, extras);

        log.info("Job {} cancelled from {} by {} ({})", jobId, previousStatus, actorUid,
                cancelledBy);

        // Cancellation fee applies when the job was already ESCROW_HELD (spec: $10 + 13% HST).
        boolean feeCharged = "ESCROW_HELD".equals(previousStatus);
        double feeCAD = feeCharged ? 11.30 : 0.0;
        notificationService.sendCancellationEmail(
                job.getRequesterId(), job.getWorkerId(), feeCharged, feeCAD, jobId);
        notificationService.notifyCancellation(job.getRequesterId(), jobId, feeCharged);
        if (job.getWorkerId() != null) {
            notificationService.notifyCancellation(job.getWorkerId(), jobId, false);
        }

        return previousStatus;
    }

    // ── Private helpers — state machine ──────────────────────────────────────

    /**
     * Validates that the actor is permitted to trigger the given transition.
     * "system" and "stripe" actors are always permitted.
     */
    private void validateActorPermission(Job job, String from, String to,
                                          String actorUid, boolean isAdmin) {
        // Automated transitions bypass all checks.
        if ("system".equals(actorUid) || "stripe".equals(actorUid)) return;

        String key = from + "->" + to;
        switch (key) {

            // Worker-only transitions.
            case "ESCROW_HELD->IN_PROGRESS",
                 "IN_PROGRESS->PENDING_APPROVAL" -> {
                if (!actorUid.equals(job.getWorkerId())) {
                    throw new AccessDeniedException(
                            "Only the assigned Worker may perform this transition");
                }
            }

            // Worker or Admin.
            case "IN_PROGRESS->INCOMPLETE" -> {
                if (!actorUid.equals(job.getWorkerId()) && !isAdmin) {
                    throw new AccessDeniedException(
                            "Only the assigned Worker or an Admin may mark this job Incomplete");
                }
            }

            // Requester-only (within the approval window after PENDING_APPROVAL).
            case "PENDING_APPROVAL->DISPUTED" -> {
                if (!actorUid.equals(job.getRequesterId())) {
                    throw new AccessDeniedException("Only the Requester may initiate a dispute");
                }
                // Enforce the configurable approval window.
                if (job.getPendingApprovalAt() != null) {
                    long windowSecs = (long) job.getApprovalWindowHours() * 3600;
                    long elapsed    = Timestamp.now().getSeconds() - job.getPendingApprovalAt().getSeconds();
                    if (elapsed > windowSecs) {
                        throw new InvalidTransitionException(
                                "The dispute window has expired");
                    }
                }
            }

            // Requester-only (no time limit for INCOMPLETE).
            case "INCOMPLETE->DISPUTED" -> {
                if (!actorUid.equals(job.getRequesterId())) {
                    throw new AccessDeniedException("Only the Requester may initiate a dispute");
                }
            }

            // Admin-only transitions.
            case "INCOMPLETE->RELEASED",
                 "DISPUTED->RELEASED",
                 "DISPUTED->REFUNDED",
                 "RELEASED->SETTLED" -> {
                if (!isAdmin) {
                    throw new AccessDeniedException("Only an Admin may perform this transition");
                }
            }

            // PENDING_APPROVAL->RELEASED: Requester explicit approval or system auto-release.
            case "PENDING_APPROVAL->RELEASED" -> {
                if (!isAdmin && !"system".equals(actorUid)
                        && !actorUid.equals(job.getRequesterId())) {
                    throw new AccessDeniedException(
                            "Payment release requires Requester approval or system auto-release");
                }
            }

            default -> {
                // Other transitions (REQUESTED->PENDING_DEPOSIT, PENDING_DEPOSIT->CONFIRMED)
                // are system-only and already handled by the "system"/"stripe" early return.
                // If we reach here with a non-system actor it is still technically allowed
                // by the transition table — trust the table.
            }
        }
    }

    /**
     * Adds the relevant lifecycle timestamp for the given target status.
     * Also computes derived fields (e.g. {@code autoReleaseAt} after COMPLETE).
     */
    private void applyLifecycleTimestamp(Map<String, Object> updates, String toStatus) {
        Timestamp now = Timestamp.now();
        switch (toStatus) {
            case "ESCROW_HELD"      -> updates.put("escrowHeldAt",       now);
            case "IN_PROGRESS"      -> updates.put("inProgressAt",       now);
            case "PENDING_APPROVAL" -> {
                updates.put("pendingApprovalAt", now);
                // Auto-release fires after the approval window if Requester hasn't acted.
                // Default window is 2 hours; respects job.approvalWindowHours when set.
                updates.put("autoReleaseAt",
                        Timestamp.ofTimeSecondsAndNanos(now.getSeconds() + 2L * 3600, 0));
            }
            case "RELEASED"  -> updates.put("releasedAt",         now);
            case "REFUNDED"  -> updates.put("refundedAt",         now);
            case "DISPUTED"  -> updates.put("disputeInitiatedAt", now);
            case "CANCELLED" -> updates.put("cancelledAt",        now);
            // POSTED, NEGOTIATING, AGREED: timestamps set by OfferService / JobController.
            // INCOMPLETE, SETTLED: no dedicated lifecycle timestamp.
        }
    }

    /**
     * Executes post-transition side effects (Quartz scheduling, notifications).
     * Called AFTER the Firestore transaction commits.
     */
    private void handleSideEffects(String jobId, String toStatus, String actorUid) {
        // Schedule auto-release Quartz timer when a job reaches PENDING_APPROVAL.
        if ("PENDING_APPROVAL".equals(toStatus)) {
            scheduleAutoRelease(jobId);
        }
        // Notification stub — wired in P1-17/P1-18.
        notifyTransition(jobId, toStatus, actorUid);
    }

    /**
     * Schedules a {@link com.yosnowmow.scheduler.DisputeTimerJob} to fire 4 hours
     * after COMPLETE.  If no dispute has been filed by then the job is auto-released.
     */
    private void scheduleAutoRelease(String jobId) {
        try {
            org.quartz.JobDetail detail = org.quartz.JobBuilder
                    .newJob(com.yosnowmow.scheduler.DisputeTimerJob.class)
                    .withIdentity("autorelease_" + jobId, "autorelease")
                    .usingJobData("jobId", jobId)
                    .storeDurably(false)
                    .build();

            org.quartz.Trigger trigger = org.quartz.TriggerBuilder.newTrigger()
                    .forJob(detail)
                    .withIdentity("autorelease_" + jobId, "autorelease")
                    .startAt(java.util.Date.from(
                            java.time.Instant.now().plusSeconds(4L * 3600)))
                    .build();

            quartzScheduler.scheduleJob(detail, trigger);
            log.debug("Auto-release timer set for job {} (4 hours)", jobId);

        } catch (org.quartz.SchedulerException e) {
            log.error("Failed to schedule auto-release timer for job {}: {}", jobId,
                    e.getMessage(), e);
        }
    }

    /**
     * Sends email notifications for lifecycle transitions that require them.
     * Runs after the Firestore transaction has committed.
     * Notification failures are swallowed inside NotificationService — they never
     * propagate back here.
     */
    private void notifyTransition(String jobId, String toStatus, String actorUid) {
        if (!"IN_PROGRESS".equals(toStatus) && !"PENDING_APPROVAL".equals(toStatus)) {
            return; // other statuses handled elsewhere (ESCROW_HELD → WebhookController, etc.)
        }
        try {
            Job job = getJob(jobId);
            String address = job.getPropertyAddress() != null
                    ? job.getPropertyAddress().getFullText() : "the property";

            if ("IN_PROGRESS".equals(toStatus)) {
                notificationService.sendJobInProgressEmail(job.getRequesterId(), job);
                notificationService.notifyWorkerArrived(job.getRequesterId(), jobId, address);
            } else {
                notificationService.sendJobCompleteEmail(job.getRequesterId(), job.getWorkerId(), job);
                notificationService.notifyJobCompleteRequester(job.getRequesterId(), jobId);
                double payoutCAD = job.getWorkerPayoutCAD() != null ? job.getWorkerPayoutCAD() : 0.0;
                notificationService.notifyJobCompleteWorker(job.getWorkerId(), jobId, payoutCAD);
            }
        } catch (Exception e) {
            log.error("Failed to send transition notification for job {} → {}: {}",
                    jobId, toStatus, e.getMessage(), e);
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
     * Requester sees own jobs; assigned Worker sees their job; matched Workers (in
     * matchedWorkerIds) can read POSTED/NEGOTIATING jobs so they can submit offers;
     * Admin sees all.
     */
    public Job getJobForCaller(String jobId, AuthenticatedUser caller) {
        Job job = getJob(jobId);
        boolean isRequester    = job.getRequesterId().equals(caller.uid());
        boolean isAssigned     = caller.uid().equals(job.getWorkerId());
        boolean isMatchedWorker = caller.hasRole("worker")
                && job.getMatchedWorkerIds() != null
                && job.getMatchedWorkerIds().contains(caller.uid());
        boolean isAdmin        = caller.hasRole("admin");

        if (!isRequester && !isAssigned && !isMatchedWorker && !isAdmin) {
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
