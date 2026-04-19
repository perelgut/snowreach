package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.yosnowmow.dto.DisputeRequest;
import com.yosnowmow.exception.InvalidTransitionException;
import com.yosnowmow.model.Dispute;
import com.yosnowmow.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Business logic for the dispute workflow (P2-01).
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Requester calls {@link #openDispute} — creates the dispute document,
 *       transitions the job to DISPUTED, notifies both parties and Admin.</li>
 *   <li>Either party may call {@link #addStatement} to submit or update their
 *       account of what happened.</li>
 *   <li>Admin calls {@link #resolveDispute} — records the decision (RELEASED,
 *       REFUNDED, or SPLIT), triggers the appropriate Stripe operation via
 *       {@link PaymentService}, and notifies both parties.</li>
 * </ol>
 *
 * <h3>Architecture notes</h3>
 * <ul>
 *   <li>Dispute documents live in a top-level {@code disputes} Firestore collection.</li>
 *   <li>The job document stores a {@code disputeId} back-reference once opened.</li>
 *   <li>Audit log entries are written BEFORE every state-changing Firestore write
 *       (consistent with the pattern in JobService).</li>
 *   <li>The 2-hour dispute window for COMPLETE jobs is enforced here AND in the
 *       state machine guard inside {@code JobService.transition()} — double validation
 *       is intentional to catch any bypass.</li>
 * </ul>
 */
@Service
public class DisputeService {

    private static final Logger log = LoggerFactory.getLogger(DisputeService.class);

    private static final String DISPUTES_COLLECTION = "disputes";
    private static final String JOBS_COLLECTION     = "jobs";

    /** Allowed resolution values passed to resolveDispute. */
    private static final Set<String> VALID_RESOLUTIONS = Set.of("RELEASED", "REFUNDED", "SPLIT");

    private final Firestore             firestore;
    private final JobService            jobService;
    private final PaymentService        paymentService;
    private final NotificationService   notificationService;
    private final AuditLogService       auditLogService;

    public DisputeService(Firestore firestore,
                          JobService jobService,
                          PaymentService paymentService,
                          NotificationService notificationService,
                          AuditLogService auditLogService) {
        this.firestore           = firestore;
        this.jobService          = jobService;
        this.paymentService      = paymentService;
        this.notificationService = notificationService;
        this.auditLogService     = auditLogService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Opens a dispute on a job that is in COMPLETE or INCOMPLETE status.
     *
     * <p>Steps:
     * <ol>
     *   <li>Read job and validate: status must be COMPLETE or INCOMPLETE;
     *       caller must be the Requester.</li>
     *   <li>Guard: job must not already be disputed.</li>
     *   <li>For COMPLETE jobs: enforce the 2-hour dispute window.</li>
     *   <li>Write the dispute document to Firestore (status = OPEN).</li>
     *   <li>Transition the job to DISPUTED via the validated state machine.</li>
     *   <li>Link the dispute ID onto the job document.</li>
     *   <li>Notify the Requester, Worker, and Admin.</li>
     * </ol>
     *
     * @param jobId       Firestore job document ID
     * @param requesterId Firebase UID of the calling Requester
     * @param req         dispute request containing the opener's statement
     * @return the newly created Dispute document
     * @throws AccessDeniedException        if the caller is not the Requester
     * @throws InvalidTransitionException   if the job is not COMPLETE/INCOMPLETE,
     *                                      is already disputed, or the window has closed
     */
    public Dispute openDispute(String jobId, String requesterId, DisputeRequest req) {

        // a. Read job and validate caller + status.
        Job job = jobService.getJob(jobId);

        if (!requesterId.equals(job.getRequesterId())) {
            throw new AccessDeniedException("Only the Requester may open a dispute");
        }

        String jobStatus = job.getStatus();
        if (!"PENDING_APPROVAL".equals(jobStatus) && !"INCOMPLETE".equals(jobStatus)) {
            throw new InvalidTransitionException(
                    "Disputes may only be opened on jobs in PENDING_APPROVAL or INCOMPLETE status"
                    + " (current: " + jobStatus + ")");
        }

        // b. Guard: not already disputed.
        if (job.getDisputeInitiatedAt() != null || job.getDisputeId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A dispute has already been opened for this job");
        }

        // c. Enforce the configurable approval window for PENDING_APPROVAL jobs.
        if ("PENDING_APPROVAL".equals(jobStatus) && job.getPendingApprovalAt() != null) {
            long windowSecs = (long) job.getApprovalWindowHours() * 3600;
            Instant pendingAt = Instant.ofEpochSecond(job.getPendingApprovalAt().getSeconds());
            if (Instant.now().isAfter(pendingAt.plusSeconds(windowSecs))) {
                throw new InvalidTransitionException("Dispute window has closed.");
            }
        }

        // d. Build and write the dispute document.
        String disputeId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.now();

        Dispute dispute = new Dispute();
        dispute.setDisputeId(disputeId);
        dispute.setJobId(jobId);
        dispute.setOpenedByUid(requesterId);
        dispute.setOpenedAt(now);
        dispute.setStatus("OPEN");
        dispute.setRequesterStatement(req.getStatement());
        dispute.setEvidenceUrls(new ArrayList<>());

        // Audit BEFORE write (established pattern — see JobService.createJob).
        auditLogService.write(requesterId, "DISPUTE_OPENED", "dispute", disputeId, null, dispute);
        writeDispute(dispute);

        // e. Transition job to DISPUTED (state machine validates the 2-hour window again).
        jobService.transition(jobId, "DISPUTED", requesterId, false);

        // f. Link the dispute ID back onto the job document.
        try {
            firestore.collection(JOBS_COLLECTION).document(jobId)
                    .update("disputeId", disputeId, "updatedAt", Timestamp.now())
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            // Non-fatal: dispute and job transition succeeded; only the back-reference was lost.
            log.error("Failed to link disputeId {} to job {}: {}", disputeId, jobId,
                    e.getMessage(), e);
        }

        // g. Notify both parties and Admin.
        notificationService.sendDisputeOpenedEmail(requesterId, job.getWorkerId(), jobId);
        notificationService.notifyDisputeOpened(requesterId, jobId, "requester");
        if (job.getWorkerId() != null) {
            notificationService.notifyDisputeOpened(job.getWorkerId(), jobId, "worker");
        }

        log.info("Dispute {} opened for job {} by requester {}", disputeId, jobId, requesterId);
        return dispute;
    }

    /**
     * Retrieves a dispute by ID.
     *
     * Access: only the Requester or Worker on the job, or an Admin.
     *
     * @param disputeId Firestore dispute document ID
     * @param callerUid Firebase UID of the requesting user
     * @param isAdmin   whether the caller holds the admin role
     * @return the Dispute document
     * @throws AccessDeniedException if the caller is not a party to the job or an Admin
     */
    public Dispute getDispute(String disputeId, String callerUid, boolean isAdmin) {
        Dispute dispute = readDispute(disputeId);

        if (!isAdmin) {
            Job job = jobService.getJob(dispute.getJobId());
            boolean isParty = callerUid.equals(job.getRequesterId())
                           || callerUid.equals(job.getWorkerId());
            if (!isParty) {
                throw new AccessDeniedException("Access denied to dispute " + disputeId);
            }
        }

        return dispute;
    }

    /**
     * Adds or replaces a party's statement on an open dispute.
     *
     * The Requester updates {@code requesterStatement};
     * the Worker updates {@code workerStatement}.
     *
     * @param disputeId Firestore dispute document ID
     * @param callerUid Firebase UID of the caller
     * @param req       statement request
     * @return the updated Dispute document
     * @throws AccessDeniedException if the caller is not a party to the job
     */
    public Dispute addStatement(String disputeId, String callerUid, DisputeRequest req) {
        Dispute dispute = readDispute(disputeId);

        if (!"OPEN".equals(dispute.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cannot add a statement to a resolved dispute");
        }

        Job job = jobService.getJob(dispute.getJobId());

        Map<String, Object> updates = new HashMap<>();
        if (callerUid.equals(job.getRequesterId())) {
            updates.put("requesterStatement", req.getStatement());
        } else if (callerUid.equals(job.getWorkerId())) {
            updates.put("workerStatement", req.getStatement());
        } else {
            throw new AccessDeniedException(
                    "Only the Requester or Worker on this job may submit a statement");
        }

        auditLogService.write(callerUid, "DISPUTE_STATEMENT_ADDED", "dispute",
                disputeId, dispute, updates);

        try {
            firestore.collection(DISPUTES_COLLECTION).document(disputeId)
                    .update(updates).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to update statement on dispute {}: {}", disputeId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update dispute statement");
        }

        log.info("Statement updated on dispute {} by {}", disputeId, callerUid);
        return readDispute(disputeId);
    }

    /**
     * Admin resolves a dispute by selecting RELEASED, REFUNDED, or SPLIT.
     *
     * <p>Steps:
     * <ol>
     *   <li>Validate: dispute must be OPEN; resolution must be a valid value.</li>
     *   <li>Update dispute document: status = RESOLVED, resolution, adminNotes, etc.</li>
     *   <li>Trigger the appropriate payment operation (which also transitions the job).</li>
     *   <li>Notify both parties of the outcome.</li>
     * </ol>
     *
     * <p>Payment methods handle their own job status transitions:
     * <ul>
     *   <li>{@code releasePayment} → job transitions to RELEASED</li>
     *   <li>{@code refundJob}      → job transitions to REFUNDED</li>
     *   <li>{@code splitPayment}   → job transitions to RELEASED</li>
     * </ul>
     *
     * @param disputeId  Firestore dispute document ID
     * @param adminUid   Firebase UID of the adjudicating Admin
     * @param resolution RELEASED | REFUNDED | SPLIT
     * @param splitPct   worker's share (0–100); only meaningful for SPLIT
     * @param adminNotes Admin's notes on the resolution decision
     * @return the resolved Dispute document
     */
    public Dispute resolveDispute(String disputeId, String adminUid,
                                  String resolution, int splitPct, String adminNotes) {

        Dispute dispute = readDispute(disputeId);

        if (!"OPEN".equals(dispute.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Dispute " + disputeId + " is already resolved");
        }

        if (!VALID_RESOLUTIONS.contains(resolution)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "resolution must be one of: RELEASED, REFUNDED, SPLIT");
        }

        Timestamp now = Timestamp.now();

        // b. Update dispute to RESOLVED.
        Map<String, Object> updates = new HashMap<>();
        updates.put("status",                  "RESOLVED");
        updates.put("resolution",              resolution);
        updates.put("splitPercentageToWorker", splitPct);
        updates.put("adminNotes",              adminNotes);
        updates.put("resolvedByAdminUid",      adminUid);
        updates.put("resolvedAt",              now);

        auditLogService.write(adminUid, "DISPUTE_RESOLVED", "dispute", disputeId, dispute, updates);

        try {
            firestore.collection(DISPUTES_COLLECTION).document(disputeId)
                    .update(updates).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to resolve dispute {}: {}", disputeId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to resolve dispute");
        }

        String jobId = dispute.getJobId();

        // c. Trigger payment action.
        // Each payment method internally handles the job status transition as well:
        //   releasePayment → RELEASED, refundJob → REFUNDED, splitPayment → RELEASED
        switch (resolution) {
            case "RELEASED" -> paymentService.releasePayment(jobId);
            case "REFUNDED" -> paymentService.refundJob(jobId);
            case "SPLIT"    -> paymentService.splitPayment(jobId, splitPct);
        }

        // d. Notify both parties.
        Job job = jobService.getJob(jobId);
        notificationService.sendDisputeResolvedEmail(
                job.getRequesterId(), job.getWorkerId(), resolution, job);
        notificationService.notifyDisputeResolved(job.getRequesterId(), jobId, resolution);
        if (job.getWorkerId() != null) {
            notificationService.notifyDisputeResolved(job.getWorkerId(), jobId, resolution);
        }

        log.info("Dispute {} resolved as {} by admin {}", disputeId, resolution, adminUid);
        return readDispute(disputeId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Reads a dispute document from Firestore.
     *
     * @param disputeId Firestore document ID
     * @return the Dispute object
     * @throws ResponseStatusException 404 if no document exists for this ID
     */
    private Dispute readDispute(String disputeId) {
        try {
            DocumentSnapshot snap = firestore.collection(DISPUTES_COLLECTION)
                    .document(disputeId).get().get();

            if (!snap.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dispute not found: " + disputeId);
            }

            Dispute dispute = snap.toObject(Dispute.class);
            if (dispute == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dispute not found: " + disputeId);
            }
            return dispute;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error fetching dispute {}: {}", disputeId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch dispute");
        }
    }

    /**
     * Writes a new dispute document to Firestore, wrapping checked exceptions.
     */
    private void writeDispute(Dispute dispute) {
        try {
            firestore.collection(DISPUTES_COLLECTION)
                    .document(dispute.getDisputeId())
                    .set(dispute).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error writing dispute {}: {}", dispute.getDisputeId(),
                    e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create dispute");
        }
    }
}
