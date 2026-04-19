package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Evaluates fraud rules before every Worker payout and manages the review lifecycle
 * for flagged payouts (P3-05).
 *
 * <h3>Rules evaluated before each payout</h3>
 * <ol>
 *   <li><b>RULE_VELOCITY</b> — Worker completed more than 5 jobs in the last 24 hours.</li>
 *   <li><b>RULE_LARGE_PAYOUT</b> — Net payout exceeds $500 CAD.</li>
 *   <li><b>RULE_RATING_MANIPULATION</b> — Simplified proxy: Worker has a suspiciously
 *       high rating (≥ 4.8) with very few total ratings (3–5).
 *       A full implementation would compare the current average to the average 10 jobs ago,
 *       which requires per-job rating history not yet in scope.</li>
 *   <li><b>RULE_NEW_ACCOUNT_PAYOUT</b> — Account is younger than 7 days AND payout exceeds
 *       $200 CAD.</li>
 * </ol>
 *
 * <h3>Flag lifecycle</h3>
 * <pre>
 *   PENDING_REVIEW → APPROVED (Admin clears; payout is then released by caller)
 *                 → REJECTED (Admin denies payout; Worker is notified)
 * </pre>
 *
 * <h3>Circular-dependency note</h3>
 * {@code FraudDetectionService} does NOT depend on {@code PaymentService}.
 * The {@code approveFraudFlag} method only clears the flag and the {@code payoutPaused}
 * marker on the job — it is the caller's responsibility (AdminController) to then
 * invoke {@code PaymentService.releasePayment()}.  This keeps the dependency graph
 * strictly one-directional.
 *
 * <h3>Firestore collection</h3>
 * {@code fraudFlags/{flagId}} — top-level collection.
 * Schema: flagId, workerUid, jobId, ruleTriggered, detectedAt,
 *         status, payoutAmountCents, reviewedByAdminUid, reviewedAt, reviewNotes.
 */
@Service
public class FraudDetectionService {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private static final String FRAUD_FLAGS_COLLECTION = "fraudFlags";
    private static final String JOBS_COLLECTION        = "jobs";
    private static final String USERS_COLLECTION       = "users";

    /** Maximum completed jobs in 24 hours before velocity flag triggers. */
    private static final int VELOCITY_THRESHOLD = 5;

    /** Net payout threshold (cents) for large-payout rule: $500 CAD. */
    private static final long LARGE_PAYOUT_THRESHOLD_CENTS = 50_000L;

    /** Net payout threshold (cents) for new-account rule: $200 CAD. */
    private static final long NEW_ACCOUNT_PAYOUT_THRESHOLD_CENTS = 20_000L;

    /** Account age (days) below which new-account rules apply. */
    private static final int NEW_ACCOUNT_DAYS = 7;

    /** Rating at which rating-manipulation proxy check triggers. */
    private static final double RATING_MANIPULATION_MIN = 4.8;

    /** Upper bound on ratingCount for the manipulation proxy (too few ratings for the average to be trusted). */
    private static final int RATING_MANIPULATION_MAX_COUNT = 5;

    /** Minimum ratingCount before the manipulation proxy applies (need at least a few data points). */
    private static final int RATING_MANIPULATION_MIN_COUNT = 3;

    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_APPROVED       = "APPROVED";
    public static final String STATUS_REJECTED       = "REJECTED";

    private final Firestore           firestore;
    private final JobService          jobService;
    private final NotificationService notificationService;
    private final AuditLogService     auditLogService;

    public FraudDetectionService(Firestore firestore,
                                 JobService jobService,
                                 NotificationService notificationService,
                                 AuditLogService auditLogService) {
        this.firestore           = firestore;
        this.jobService          = jobService;
        this.notificationService = notificationService;
        this.auditLogService     = auditLogService;
    }

    // ── Pre-payout check ─────────────────────────────────────────────────────

    /**
     * Runs all fraud rules for a job that is about to receive a Worker payout.
     *
     * <p>If any rule triggers:
     * <ol>
     *   <li>A {@code fraudFlags} document is created with status {@code PENDING_REVIEW}.</li>
     *   <li>The job's {@code payoutPaused} field is set to {@code true}.</li>
     *   <li>The Worker receives a "payment under review" email.</li>
     *   <li>The Admin receives a fraud-flag alert.</li>
     * </ol>
     *
     * @param jobId Firestore document ID of the job
     * @return {@code true} if the payout is safe to proceed; {@code false} if flagged
     * @throws InterruptedException on thread interruption
     * @throws ExecutionException   on Firestore error
     */
    public boolean checkBeforePayout(String jobId)
            throws InterruptedException, ExecutionException {

        Job job = jobService.getJob(jobId);
        String workerUid = job.getWorkerId();

        if (workerUid == null) {
            log.warn("FraudCheck: job {} has no assigned Worker — skipping rules", jobId);
            return true;
        }

        // Total payout in cents (net worker amount + HST if applicable)
        long payoutCents = 0;
        if (job.getWorkerPayoutCAD() != null) {
            payoutCents = Math.round(job.getWorkerPayoutCAD() * 100);
            if (job.getHstAmountCAD() != null) {
                payoutCents += Math.round(job.getHstAmountCAD() * 100);
            }
        }

        // Load Worker profile for account-level rules
        var workerSnap = firestore.collection(USERS_COLLECTION).document(workerUid).get().get();
        User       workerUser    = workerSnap.exists() ? workerSnap.toObject(User.class) : null;
        WorkerProfile workerProfile = (workerUser != null) ? workerUser.getWorker() : null;

        List<String> triggeredRules = new ArrayList<>();

        // ── Rule 1: Velocity ─────────────────────────────────────────────────
        Timestamp cutoff24h = Timestamp.ofTimeSecondsAndNanos(
                Instant.now().minus(24, ChronoUnit.HOURS).getEpochSecond(), 0);

        QuerySnapshot recentCompleted = firestore.collection(JOBS_COLLECTION)
                .whereEqualTo("workerId", workerUid)
                .whereEqualTo("status",   "PENDING_APPROVAL")
                .whereGreaterThan("pendingApprovalAt", cutoff24h)
                .get().get();

        if (recentCompleted.size() > VELOCITY_THRESHOLD) {
            log.warn("FraudCheck RULE_VELOCITY: worker {} completed {} jobs in 24h (max {})",
                    workerUid, recentCompleted.size(), VELOCITY_THRESHOLD);
            triggeredRules.add("RULE_VELOCITY");
        }

        // ── Rule 2: Large payout ─────────────────────────────────────────────
        if (payoutCents > LARGE_PAYOUT_THRESHOLD_CENTS) {
            log.warn("FraudCheck RULE_LARGE_PAYOUT: worker {} payout {} ¢ (threshold {} ¢)",
                    workerUid, payoutCents, LARGE_PAYOUT_THRESHOLD_CENTS);
            triggeredRules.add("RULE_LARGE_PAYOUT");
        }

        // ── Rule 3: Rating manipulation (simplified proxy) ───────────────────
        // Full rule: compare current avg to rating 10 jobs ago (requires per-job history).
        // Proxy: flag if suspiciously high rating (≥ 4.8) with very few total ratings (3–5).
        if (workerProfile != null
                && workerProfile.getRating() != null
                && workerProfile.getRating() >= RATING_MANIPULATION_MIN
                && workerProfile.getRatingCount() >= RATING_MANIPULATION_MIN_COUNT
                && workerProfile.getRatingCount() <= RATING_MANIPULATION_MAX_COUNT) {
            log.warn("FraudCheck RULE_RATING_MANIPULATION: worker {} rating={} ratingCount={}",
                    workerUid, workerProfile.getRating(), workerProfile.getRatingCount());
            triggeredRules.add("RULE_RATING_MANIPULATION");
        }

        // ── Rule 4: New account + large payout ───────────────────────────────
        if (workerUser != null
                && workerUser.getCreatedAt() != null
                && payoutCents > NEW_ACCOUNT_PAYOUT_THRESHOLD_CENTS) {

            long accountAgeDays = (Timestamp.now().toDate().getTime()
                    - workerUser.getCreatedAt().toDate().getTime())
                    / (1000L * 60 * 60 * 24);

            if (accountAgeDays < NEW_ACCOUNT_DAYS) {
                log.warn("FraudCheck RULE_NEW_ACCOUNT_PAYOUT: worker {} (age {} day(s)) payout {} ¢",
                        workerUid, accountAgeDays, payoutCents);
                triggeredRules.add("RULE_NEW_ACCOUNT_PAYOUT");
            }
        }

        if (triggeredRules.isEmpty()) {
            log.debug("FraudCheck: all rules passed for job {} (worker {})", jobId, workerUid);
            return true; // safe to proceed
        }

        // ── At least one rule triggered ───────────────────────────────────────
        String flagId      = UUID.randomUUID().toString();
        String rulesString = String.join(", ", triggeredRules);

        Map<String, Object> flagDoc = new HashMap<>();
        flagDoc.put("flagId",            flagId);
        flagDoc.put("workerUid",         workerUid);
        flagDoc.put("jobId",             jobId);
        flagDoc.put("ruleTriggered",     rulesString);
        flagDoc.put("detectedAt",        Timestamp.now());
        flagDoc.put("status",            STATUS_PENDING_REVIEW);
        flagDoc.put("payoutAmountCents", payoutCents);

        firestore.collection(FRAUD_FLAGS_COLLECTION).document(flagId).set(flagDoc).get();

        // Pause the payout on the job document
        Map<String, Object> jobUpdate = new HashMap<>();
        jobUpdate.put("payoutPaused", true);
        jobUpdate.put("updatedAt",    Timestamp.now());
        firestore.collection(JOBS_COLLECTION).document(jobId).update(jobUpdate).get();

        auditLogService.write("system", "FRAUD_FLAG_RAISED", "job", jobId,
                null, Map.of("flagId", flagId, "rulesTriggered", rulesString));

        notificationService.sendPayoutUnderReview(workerUid, jobId);
        notificationService.notifyAdminFraudFlag(workerUid, jobId, triggeredRules);

        log.warn("FraudCheck: job {} FLAGGED — rules: {} — payout paused (flagId={})",
                jobId, rulesString, flagId);
        return false; // payout blocked
    }

    // ── Admin review actions ─────────────────────────────────────────────────

    /**
     * Approves a fraud flag after admin review.
     *
     * <p>Sets flag status to {@code APPROVED} and clears {@code payoutPaused} on the
     * job so that a subsequent call to {@code PaymentService.releasePayment(jobId)}
     * will pass the fraud check (no PENDING_REVIEW flag will exist).
     *
     * @param flagId   fraud flag document ID
     * @param adminUid UID of the reviewing admin
     * @param notes    optional review notes (may be null)
     * @return the {@code jobId} from the flag document — caller uses this to release payment
     * @throws ResponseStatusException 404 if flag not found; 409 if not PENDING_REVIEW
     */
    public String approveFraudFlag(String flagId, String adminUid, String notes)
            throws InterruptedException, ExecutionException {

        Map<String, Object> flag = fetchFlag(flagId);
        guardPendingReview(flagId, (String) flag.get("status"));

        String jobId     = (String) flag.get("jobId");
        String workerUid = (String) flag.get("workerUid");

        // Mark flag as APPROVED
        Map<String, Object> flagUpdate = new HashMap<>();
        flagUpdate.put("status",             STATUS_APPROVED);
        flagUpdate.put("reviewedByAdminUid", adminUid);
        flagUpdate.put("reviewedAt",         Timestamp.now());
        if (notes != null && !notes.isBlank()) {
            flagUpdate.put("reviewNotes", notes);
        }
        firestore.collection(FRAUD_FLAGS_COLLECTION).document(flagId).update(flagUpdate).get();

        // Clear payoutPaused so the next releasePayment call proceeds
        Map<String, Object> jobUpdate = new HashMap<>();
        jobUpdate.put("payoutPaused", false);
        jobUpdate.put("updatedAt",    Timestamp.now());
        firestore.collection(JOBS_COLLECTION).document(jobId).update(jobUpdate).get();

        auditLogService.write(adminUid, "FRAUD_FLAG_APPROVED", "fraudFlag", flagId,
                STATUS_PENDING_REVIEW, STATUS_APPROVED);

        log.info("Admin {} APPROVED fraud flag {} for job {} (worker {})",
                adminUid, flagId, jobId, workerUid);
        return jobId;
    }

    /**
     * Rejects a fraud flag after admin review.
     *
     * <p>Sets flag status to {@code REJECTED} and notifies the Worker that their
     * payout has been denied.  The Requester refund (if applicable) is handled
     * separately via the standard refund endpoint.
     *
     * @param flagId   fraud flag document ID
     * @param adminUid UID of the reviewing admin
     * @param notes    optional review notes (may be null)
     * @throws ResponseStatusException 404 if flag not found; 409 if not PENDING_REVIEW
     */
    public void rejectFraudFlag(String flagId, String adminUid, String notes)
            throws InterruptedException, ExecutionException {

        Map<String, Object> flag = fetchFlag(flagId);
        guardPendingReview(flagId, (String) flag.get("status"));

        String jobId     = (String) flag.get("jobId");
        String workerUid = (String) flag.get("workerUid");

        // Mark flag as REJECTED
        Map<String, Object> flagUpdate = new HashMap<>();
        flagUpdate.put("status",             STATUS_REJECTED);
        flagUpdate.put("reviewedByAdminUid", adminUid);
        flagUpdate.put("reviewedAt",         Timestamp.now());
        if (notes != null && !notes.isBlank()) {
            flagUpdate.put("reviewNotes", notes);
        }
        firestore.collection(FRAUD_FLAGS_COLLECTION).document(flagId).update(flagUpdate).get();

        auditLogService.write(adminUid, "FRAUD_FLAG_REJECTED", "fraudFlag", flagId,
                STATUS_PENDING_REVIEW, STATUS_REJECTED);

        notificationService.sendPayoutDenied(workerUid, jobId);

        log.info("Admin {} REJECTED fraud flag {} for job {} (worker {})",
                adminUid, flagId, jobId, workerUid);
    }

    /**
     * Returns fraud flags, optionally filtered by status.
     *
     * @param status one of {@code PENDING_REVIEW}, {@code APPROVED}, {@code REJECTED};
     *               pass {@code null} or blank to return all flags
     * @return list of flag documents ordered by {@code detectedAt} descending
     */
    public List<Map<String, Object>> getFraudFlags(String status)
            throws InterruptedException, ExecutionException {

        QuerySnapshot snap;
        if (status != null && !status.isBlank()) {
            snap = firestore.collection(FRAUD_FLAGS_COLLECTION)
                    .whereEqualTo("status", status)
                    .orderBy("detectedAt", Query.Direction.DESCENDING)
                    .get().get();
        } else {
            snap = firestore.collection(FRAUD_FLAGS_COLLECTION)
                    .orderBy("detectedAt", Query.Direction.DESCENDING)
                    .get().get();
        }

        return snap.getDocuments().stream()
                .map(QueryDocumentSnapshot::getData)
                .collect(Collectors.toList());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches a fraud flag document; throws 404 if it does not exist.
     */
    private Map<String, Object> fetchFlag(String flagId)
            throws InterruptedException, ExecutionException {

        var snap = firestore.collection(FRAUD_FLAGS_COLLECTION).document(flagId).get().get();
        if (!snap.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Fraud flag not found: " + flagId);
        }
        return snap.getData();
    }

    /**
     * Throws 409 if the flag is not in {@code PENDING_REVIEW} state.
     */
    private void guardPendingReview(String flagId, String currentStatus) {
        if (!STATUS_PENDING_REVIEW.equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Fraud flag " + flagId + " is not in PENDING_REVIEW state "
                            + "(current: " + currentStatus + ")");
        }
    }
}
