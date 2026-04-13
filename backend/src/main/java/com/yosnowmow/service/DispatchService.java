package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.JobRequest;
import com.yosnowmow.model.PricingTier;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import com.yosnowmow.scheduler.DispatchJob;
import com.yosnowmow.util.GeoUtils;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the sequential Worker dispatch loop after {@link MatchingService} has
 * populated {@code job.matchedWorkerIds}.
 *
 * <h3>Dispatch loop</h3>
 * <ol>
 *   <li>Pick the first Worker in {@code matchedWorkerIds} not yet in
 *       {@code contactedWorkerIds}.</li>
 *   <li>Write a {@code jobRequests/{jobId}_{workerId}} document (status=PENDING).</li>
 *   <li>Notify the Worker (stub — wired in P1-18).</li>
 *   <li>Schedule a Quartz {@link DispatchJob} to fire in 10 minutes.</li>
 *   <li a>Worker accepts → transition job to PENDING_DEPOSIT; compute and store pricing.</li>
 *   <li b>Worker declines / timer fires → mark DECLINED/EXPIRED, move to next Worker.</li>
 *   <li>No untried Workers remain → cancel the job, notify Requester.</li>
 * </ol>
 *
 * <h3>Startup recovery</h3>
 * {@link #recoverPendingDispatches()} runs after Spring context refresh.  It
 * re-queues Quartz timers for any PENDING jobRequest documents that survived a
 * container restart, preventing silent offer stalls in Cloud Run's stateless env.
 *
 * <h3>Pricing on accept</h3>
 * Pricing fields ({@code tierPriceCAD}, {@code hstAmountCAD}, {@code totalAmountCAD},
 * {@code workerPayoutCAD}, {@code commissionRateApplied}) are computed and stored
 * when a Worker accepts.  The Stripe escrow amount (P1-11) reads {@code totalAmountCAD}
 * from the job document.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private static final String JOBS_COLLECTION          = "jobs";
    private static final String JOB_REQUESTS_COLLECTION  = "jobRequests";
    private static final String USERS_COLLECTION         = "users";

    /** Duration of each dispatch offer window — 10 minutes. */
    private static final long OFFER_DURATION_SECONDS = 10L * 60;

    /** Quartz job-group name for all dispatch timer jobs. */
    static final String DISPATCH_JOB_GROUP = "dispatch";

    /** Standard platform commission rate (15%). */
    private static final double COMMISSION_RATE_STANDARD = 0.15;

    /** Early-adopter commission rate (8%). */
    private static final double COMMISSION_RATE_EARLY_ADOPTER = 0.08;

    /** Founding-worker commission rate (0%). */
    private static final double COMMISSION_RATE_FOUNDING = 0.0;

    /** Ontario HST rate (13%). */
    private static final double HST_RATE = 0.13;

    /**
     * Guards against double-firing of the startup recovery listener.
     * {@code ContextRefreshedEvent} can fire more than once (root + web context).
     */
    private final AtomicBoolean recoveryRun = new AtomicBoolean(false);

    private final Firestore firestore;
    private final Scheduler quartzScheduler;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public DispatchService(Firestore firestore,
                           Scheduler quartzScheduler,
                           NotificationService notificationService,
                           AuditLogService auditLogService) {
        this.firestore = firestore;
        this.quartzScheduler = quartzScheduler;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Selects the next untried Worker from the job's candidate list and sends them
     * a 10-minute offer.  If no untried Workers remain the job is CANCELLED.
     *
     * @param jobId Firestore document ID of the job to dispatch
     */
    public void dispatchToNextWorker(String jobId) {
        log.info("Dispatching next worker for job {}", jobId);
        try {
            Job job = fetchJob(jobId);
            if (job == null) {
                log.error("dispatchToNextWorker: job {} not found", jobId);
                return;
            }

            // Only dispatch jobs that are still awaiting a Worker.
            if (!"REQUESTED".equals(job.getStatus())) {
                log.info("Job {} is {} — dispatch skipped", jobId, job.getStatus());
                return;
            }

            List<String> matched   = nullSafe(job.getMatchedWorkerIds());
            List<String> contacted = nullSafe(job.getContactedWorkerIds());

            Optional<String> next = matched.stream()
                    .filter(uid -> !contacted.contains(uid))
                    .findFirst();

            if (next.isEmpty()) {
                cancelJobNoWorkers(jobId, job);
                return;
            }

            sendOffer(jobId, next.get());

        } catch (Exception e) {
            log.error("Dispatch error for job {}: {}", jobId, e.getMessage(), e);
        }
    }

    /**
     * Processes a Worker's accept or decline response to a job offer.
     *
     * <ul>
     *   <li>Accept: compute pricing, transition job to PENDING_DEPOSIT, cancel timer.</li>
     *   <li>Decline: mark DECLINED, cancel timer, dispatch to next Worker.</li>
     * </ul>
     *
     * @param requestId composite ID "{jobId}_{workerId}"
     * @param workerId  UID from the caller's Firebase auth token
     * @param accepted  true = accept, false = decline
     * @throws ResponseStatusException 404 if the offer document does not exist
     * @throws ResponseStatusException 409 if the offer is no longer PENDING
     * @throws ResponseStatusException 403 if the caller is not the intended recipient
     */
    public void handleWorkerResponse(String requestId, String workerId, boolean accepted) {
        try {
            DocumentSnapshot reqSnap = firestore.collection(JOB_REQUESTS_COLLECTION)
                    .document(requestId).get().get();

            if (!reqSnap.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Job request not found: " + requestId);
            }

            JobRequest req = reqSnap.toObject(JobRequest.class);
            if (req == null || !"PENDING".equals(req.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This offer is no longer available (status: "
                        + (req != null ? req.getStatus() : "unknown") + ")");
            }

            if (!workerId.equals(req.getWorkerId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "You are not the intended recipient of this offer");
            }

            Timestamp now  = Timestamp.now();
            String jobId   = req.getJobId();

            if (accepted) {
                acceptOffer(jobId, workerId, requestId, now);
            } else {
                declineOffer(jobId, workerId, requestId, now);
            }

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error processing worker response {}: {}", requestId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to process worker response");
        }
    }

    /**
     * Called by {@link DispatchJob} when the 10-minute offer window expires.
     *
     * If the request is still PENDING (worker didn't respond), marks it EXPIRED
     * and dispatches to the next Worker.
     *
     * @param jobId    job document ID
     * @param workerId Worker UID who failed to respond
     */
    public void handleOfferExpiry(String jobId, String workerId) {
        String requestId = jobId + "_" + workerId;
        log.info("Offer expired: job={} worker={}", jobId, workerId);
        try {
            DocumentSnapshot snap = firestore.collection(JOB_REQUESTS_COLLECTION)
                    .document(requestId).get().get();

            if (!snap.exists()) {
                log.warn("handleOfferExpiry: request {} not found — skipping", requestId);
                return;
            }

            JobRequest req = snap.toObject(JobRequest.class);
            if (req == null || !"PENDING".equals(req.getStatus())) {
                // Already accepted or declined while Quartz timer was in flight.
                log.debug("handleOfferExpiry: request {} already {} — skipping",
                        requestId, req != null ? req.getStatus() : "null");
                return;
            }

            firestore.collection(JOB_REQUESTS_COLLECTION)
                     .document(requestId)
                     .update("status", "EXPIRED")
                     .get();

            markWorkerContacted(jobId, workerId);
            dispatchToNextWorker(jobId);

        } catch (Exception e) {
            log.error("handleOfferExpiry failed for {}/{}: {}", jobId, workerId, e.getMessage(), e);
        }
    }

    /**
     * Reschedules Quartz timers for PENDING offers that survived a container restart.
     *
     * Fires once after the Spring context is fully refreshed.  The actual Firestore
     * query runs in a background daemon thread so that it does not block application
     * startup — Cloud Run's gRPC TLS negotiation can take several minutes on a cold
     * start, and a blocking {@code .get().get()} here would delay the "Started" log
     * line (and the Cloud Run readiness probe) by the same amount.
     *
     * Uses an {@link AtomicBoolean} to guard against double-execution (Spring fires
     * {@code ContextRefreshedEvent} for both the root and web application contexts).
     */
    @EventListener(ContextRefreshedEvent.class)
    public void recoverPendingDispatches() {
        if (!recoveryRun.compareAndSet(false, true)) {
            return; // already ran
        }
        Thread recovery = new Thread(this::runDispatchRecovery, "dispatch-recovery");
        recovery.setDaemon(true);
        recovery.start();
    }

    /**
     * Body of the startup recovery, executed in the {@code dispatch-recovery} daemon
     * thread.  Queries Firestore for PENDING jobRequest documents and either
     * reschedules their Quartz timers (if not yet expired) or handles them as expired
     * (triggering the next-worker dispatch).
     */
    private void runDispatchRecovery() {
        log.info("Recovering in-flight dispatch timers from Firestore…");
        try {
            QuerySnapshot snap = firestore.collection(JOB_REQUESTS_COLLECTION)
                    .whereEqualTo("status", "PENDING")
                    .get().get();

            Instant now  = Instant.now();
            int recovered = 0;
            int expired   = 0;

            for (QueryDocumentSnapshot doc : snap.getDocuments()) {
                JobRequest req = doc.toObject(JobRequest.class);
                if (req == null || req.getJobId() == null || req.getWorkerId() == null) continue;

                Instant expiresAt = req.getExpiresAt() != null
                        ? Instant.ofEpochSecond(req.getExpiresAt().getSeconds())
                        : now; // treat missing expiry as already past

                if (expiresAt.isAfter(now)) {
                    long remainingMs = expiresAt.toEpochMilli() - now.toEpochMilli();
                    scheduleQuartzTimer(req.getJobId(), req.getWorkerId(), remainingMs);
                    recovered++;
                } else {
                    handleOfferExpiry(req.getJobId(), req.getWorkerId());
                    expired++;
                }
            }

            log.info("Dispatch recovery complete: {} rescheduled, {} immediately expired",
                    recovered, expired);

        } catch (Exception e) {
            log.error("Dispatch recovery failed: {}", e.getMessage(), e);
        }
    }

    // ── Private helpers — accept / decline ────────────────────────────────────

    /**
     * Handles a Worker accepting an offer.
     *
     * Computes pricing from the Worker's tier list and the job–worker distance,
     * writes all pricing fields to the job document, then transitions to PENDING_DEPOSIT.
     * The Stripe escrow (P1-11) will read {@code totalAmountCAD} from the document.
     */
    private void acceptOffer(String jobId, String workerId, String requestId, Timestamp now)
            throws InterruptedException, ExecutionException {

        cancelQuartzTimer(requestId);

        // Update jobRequest document.
        firestore.collection(JOB_REQUESTS_COLLECTION).document(requestId).update(
                "status",      "ACCEPTED",
                "respondedAt", now
        ).get();

        Job job = fetchJob(jobId);
        if (job == null) {
            log.error("acceptOffer: job {} disappeared after offer accepted", jobId);
            return;
        }

        // Compute pricing fields before writing to the job document.
        Map<String, Object> pricingFields = computePricing(job, workerId);

        // Build the full set of job updates.
        Map<String, Object> updates = new HashMap<>(pricingFields);
        updates.put("status",      "PENDING_DEPOSIT");
        updates.put("workerId",    workerId);
        updates.put("acceptedAt",  now);
        updates.put("updatedAt",   now);
        // Requester has 30 minutes to complete the Stripe deposit.
        updates.put("escrowDepositExpiry",
                Timestamp.ofTimeSecondsAndNanos(now.getSeconds() + 30L * 60, 0));

        auditLogService.write(workerId, "JOB_ACCEPTED", "job", jobId, job, updates);

        firestore.collection(JOBS_COLLECTION).document(jobId).update(updates).get();

        notificationService.notifyRequesterJobAccepted(job.getRequesterId(), jobId, workerId);

        log.info("Job {} accepted by worker {} — status → PENDING_DEPOSIT; total={}",
                jobId, workerId, pricingFields.get("totalAmountCAD"));
    }

    /**
     * Handles a Worker declining an offer.
     * Cancels the Quartz timer, records the decline, and dispatches to the next Worker.
     */
    private void declineOffer(String jobId, String workerId, String requestId, Timestamp now)
            throws InterruptedException, ExecutionException {

        cancelQuartzTimer(requestId);

        firestore.collection(JOB_REQUESTS_COLLECTION).document(requestId).update(
                "status",      "DECLINED",
                "respondedAt", now
        ).get();

        markWorkerContacted(jobId, workerId);

        log.info("Job {} declined by worker {}", jobId, workerId);
        dispatchToNextWorker(jobId);
    }

    // ── Private helpers — pricing ──────────────────────────────────────────────

    /**
     * Computes all pricing fields at the moment a Worker accepts a job.
     *
     * Algorithm:
     * <ol>
     *   <li>Fetch the Worker's user document to get their tier list and commission status.</li>
     *   <li>Compute Haversine distance from the job property to the worker's base.</li>
     *   <li>Find the first tier whose {@code maxDistanceKm ≥ distance}.</li>
     *   <li>Apply HST (13%) if worker is HST-registered.</li>
     *   <li>Apply commission (15% standard, 8% early-adopter, 0% founding).</li>
     * </ol>
     *
     * @param job      the job document (already fetched)
     * @param workerId UID of the accepting Worker
     * @return map of pricing field names → values, ready for a Firestore update call
     */
    private Map<String, Object> computePricing(Job job, String workerId)
            throws InterruptedException, ExecutionException {

        Map<String, Object> fields = new HashMap<>();

        DocumentSnapshot workerDoc = firestore.collection(USERS_COLLECTION)
                .document(workerId).get().get();

        if (!workerDoc.exists()) {
            log.error("computePricing: worker {} document not found", workerId);
            return fields;
        }

        User workerUser = workerDoc.toObject(User.class);
        if (workerUser == null || workerUser.getWorker() == null) {
            log.error("computePricing: worker {} has no WorkerProfile", workerId);
            return fields;
        }

        WorkerProfile profile = workerUser.getWorker();

        // Distance from job to worker base (needed to select the correct pricing tier).
        double distanceKm = 0.0;
        if (job.getPropertyCoords() != null && profile.getBaseCoords() != null) {
            distanceKm = GeoUtils.haversineDistanceKm(
                    job.getPropertyCoords().getLatitude(),
                    job.getPropertyCoords().getLongitude(),
                    profile.getBaseCoords().getLatitude(),
                    profile.getBaseCoords().getLongitude());
        }

        // Select the applicable pricing tier.
        double tierPriceCAD = selectTierPrice(profile.getTiers(), distanceKm);

        // HST applies only for HST-registered workers.
        double hstAmountCAD = profile.isHstRegistered() ? tierPriceCAD * HST_RATE : 0.0;

        // Round to 2 decimal places.
        hstAmountCAD = Math.round(hstAmountCAD * 100.0) / 100.0;

        double totalAmountCAD = Math.round((tierPriceCAD + hstAmountCAD) * 100.0) / 100.0;

        // Commission rate.
        double commissionRate;
        if (profile.isFoundingWorker()) {
            commissionRate = COMMISSION_RATE_FOUNDING;
        } else if (profile.isEarlyAdopter()
                && profile.getEarlyAdopterCommissionJobsRemaining() > 0) {
            commissionRate = COMMISSION_RATE_EARLY_ADOPTER;
        } else {
            commissionRate = COMMISSION_RATE_STANDARD;
        }

        double workerPayoutCAD = Math.round(tierPriceCAD * (1.0 - commissionRate) * 100.0) / 100.0;

        fields.put("tierPriceCAD",          tierPriceCAD);
        fields.put("hstAmountCAD",           hstAmountCAD);
        fields.put("totalAmountCAD",         totalAmountCAD);
        fields.put("commissionRateApplied",  commissionRate);
        fields.put("workerPayoutCAD",        workerPayoutCAD);

        return fields;
    }

    /**
     * Returns the {@code price} from the first tier whose {@code maxDistanceKm ≥ distanceKm}.
     * Falls back to the price of the last tier if distance exceeds all tier boundaries.
     * Returns 0.0 if the tier list is null or empty.
     *
     * @param tiers       ordered list of pricing tiers (ascending maxDistanceKm)
     * @param distanceKm  computed job–worker distance in kilometres
     */
    private double selectTierPrice(List<PricingTier> tiers, double distanceKm) {
        if (tiers == null || tiers.isEmpty()) {
            log.warn("Worker has no pricing tiers — defaulting tier price to 0");
            return 0.0;
        }
        for (PricingTier tier : tiers) {
            if (distanceKm <= tier.getMaxDistanceKm()) {
                return tier.getPriceCAD();
            }
        }
        // Distance exceeds all tier boundaries — use the outermost tier's price.
        return tiers.get(tiers.size() - 1).getPriceCAD();
    }

    // ── Private helpers — dispatch mechanics ──────────────────────────────────

    /**
     * Writes the jobRequest document, notifies the Worker, and schedules the expiry timer.
     */
    private void sendOffer(String jobId, String workerId)
            throws InterruptedException, ExecutionException {

        Timestamp now      = Timestamp.now();
        Timestamp expiresAt = Timestamp.ofTimeSecondsAndNanos(
                now.getSeconds() + OFFER_DURATION_SECONDS, 0);

        String requestId = jobId + "_" + workerId;

        // Write offer record before notifying the worker.
        Map<String, Object> doc = new HashMap<>();
        doc.put("jobRequestId", requestId);
        doc.put("jobId",        jobId);
        doc.put("workerId",     workerId);
        doc.put("status",       "PENDING");
        doc.put("sentAt",       now);
        doc.put("expiresAt",    expiresAt);

        firestore.collection(JOB_REQUESTS_COLLECTION).document(requestId).set(doc).get();

        // Record on the job which worker currently holds an active offer.
        firestore.collection(JOBS_COLLECTION).document(jobId).update(
                "simultaneousOfferWorkerIds", FieldValue.arrayUnion(workerId),
                "offeredAt",                  now,
                "offerExpiry",                expiresAt,
                "updatedAt",                  now
        ).get();

        notificationService.sendJobRequest(workerId, jobId);
        scheduleQuartzTimer(jobId, workerId, OFFER_DURATION_SECONDS * 1000L);

        log.info("Offer sent: job={} worker={} expires={}", jobId, workerId, expiresAt);
    }

    /**
     * Transitions the job to CANCELLED when no Workers remain to dispatch.
     * Notifies the Requester (stub — wired in P1-17).
     */
    private void cancelJobNoWorkers(String jobId, Job job) {
        log.warn("No eligible workers remain for job {} — cancelling", jobId);
        try {
            auditLogService.write("system", "JOB_CANCELLED_NO_WORKERS", "job", jobId, job, null);

            firestore.collection(JOBS_COLLECTION).document(jobId).update(
                    "status",      "CANCELLED",
                    "cancelledAt", Timestamp.now(),
                    "cancelledBy", "system",
                    "updatedAt",   Timestamp.now()
            ).get();

            notificationService.notifyRequesterNoWorkers(job.getRequesterId(), jobId);

        } catch (Exception e) {
            log.error("Failed to cancel job {}: {}", jobId, e.getMessage(), e);
        }
    }

    /**
     * Moves a Worker from {@code simultaneousOfferWorkerIds} to {@code contactedWorkerIds}
     * on the job document.  Called after a decline or expiry.
     */
    private void markWorkerContacted(String jobId, String workerId) {
        try {
            firestore.collection(JOBS_COLLECTION).document(jobId).update(
                    "contactedWorkerIds",         FieldValue.arrayUnion(workerId),
                    "simultaneousOfferWorkerIds", FieldValue.arrayRemove(workerId),
                    "updatedAt",                  Timestamp.now()
            ).get();
        } catch (Exception e) {
            log.error("Failed to mark worker {} contacted on job {}: {}", workerId, jobId,
                    e.getMessage());
        }
    }

    // ── Private helpers — Quartz ───────────────────────────────────────────────

    /**
     * Schedules a {@link DispatchJob} to fire after {@code delayMs} milliseconds.
     *
     * Job key: {@code dispatch/{jobId}_{workerId}} — unique per offer, so reschedules
     * for recovery are idempotent (the old trigger is replaced by the new one).
     */
    private void scheduleQuartzTimer(String jobId, String workerId, long delayMs) {
        try {
            String requestId = jobId + "_" + workerId;
            JobKey key = JobKey.jobKey(requestId, DISPATCH_JOB_GROUP);

            // Remove any lingering timer for this key before rescheduling.
            quartzScheduler.deleteJob(key);

            JobDetail jobDetail = JobBuilder.newJob(DispatchJob.class)
                    .withIdentity(key)
                    .usingJobData("jobId",    jobId)
                    .usingJobData("workerId", workerId)
                    .storeDurably(false)
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .forJob(jobDetail)
                    .withIdentity(TriggerKey.triggerKey(requestId, DISPATCH_JOB_GROUP))
                    .startAt(Date.from(Instant.now().plusMillis(delayMs)))
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withMisfireHandlingInstructionFireNow())
                    .build();

            quartzScheduler.scheduleJob(jobDetail, trigger);
            log.debug("Quartz timer set: {} in {}ms", requestId, delayMs);

        } catch (SchedulerException e) {
            log.error("Failed to schedule Quartz timer for {}/{}: {}",
                    jobId, workerId, e.getMessage(), e);
        }
    }

    /** Deletes the Quartz expiry timer for an offer — called on accept or decline. */
    private void cancelQuartzTimer(String requestId) {
        try {
            quartzScheduler.deleteJob(JobKey.jobKey(requestId, DISPATCH_JOB_GROUP));
        } catch (SchedulerException e) {
            log.warn("Could not cancel Quartz timer for {}: {}", requestId, e.getMessage());
        }
    }

    private Job fetchJob(String jobId) throws InterruptedException, ExecutionException {
        var snap = firestore.collection(JOBS_COLLECTION).document(jobId).get().get();
        return snap.exists() ? snap.toObject(Job.class) : null;
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}
