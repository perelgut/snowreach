package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Manages the insurance declaration lifecycle for Workers (P3-03).
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Worker calls {@code POST /api/users/{uid}/worker/insurance} with a PDF and expiry date.</li>
 *   <li>{@link #uploadInsuranceDoc} uploads the file to Firebase Storage, updates the Worker
 *       profile with {@code insuranceStatus = PENDING_REVIEW}, and notifies the Admin.</li>
 *   <li>Admin reviews the document and calls {@code POST /api/admin/workers/{uid}/insurance-verify}.</li>
 *   <li>{@link #adminVerifyInsurance} sets {@code insuranceStatus} to {@code VALID} (approved)
 *       or {@code NONE} (rejected), and notifies the Worker.</li>
 * </ol>
 *
 * <h3>Annual renewal</h3>
 * {@link com.yosnowmow.scheduler.InsuranceRenewalJob} runs daily at 4 AM and:
 * <ul>
 *   <li>Sets {@code EXPIRING_SOON} and sends a reminder when expiry is ≤ 30 days away
 *       (at most once every 7 days).</li>
 *   <li>Sets {@code EXPIRED}, clears {@code worker.isActive = false}, and notifies the
 *       Worker when the expiry date has passed.</li>
 * </ul>
 *
 * <h3>Insurance status values</h3>
 * <pre>
 *   NONE           — no document uploaded (initial / after rejection)
 *   PENDING_REVIEW — document uploaded, Admin has not yet acted
 *   VALID          — Admin approved; Worker displays "Insured" trust badge
 *   EXPIRING_SOON  — valid but expires within 30 days
 *   EXPIRED        — policy has expired; Worker isActive = false
 * </pre>
 */
@Service
public class InsuranceService {

    private static final Logger log = LoggerFactory.getLogger(InsuranceService.class);

    private static final String USERS_COLLECTION = "users";

    public static final String STATUS_NONE            = "NONE";
    public static final String STATUS_PENDING_REVIEW  = "PENDING_REVIEW";
    public static final String STATUS_VALID           = "VALID";
    public static final String STATUS_EXPIRING_SOON   = "EXPIRING_SOON";
    public static final String STATUS_EXPIRED         = "EXPIRED";

    private final Firestore             firestore;
    private final StorageService        storageService;
    private final AuditLogService       auditLogService;
    private final NotificationService   notificationService;
    private final BadgeService          badgeService;

    public InsuranceService(Firestore firestore,
                            StorageService storageService,
                            AuditLogService auditLogService,
                            NotificationService notificationService,
                            BadgeService badgeService) {
        this.firestore           = firestore;
        this.storageService      = storageService;
        this.auditLogService     = auditLogService;
        this.notificationService = notificationService;
        this.badgeService        = badgeService;
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    /**
     * Handles a Worker's insurance document upload.
     *
     * <p>Uploads the PDF to Firebase Storage at {@code workers/{uid}/insurance/{uuid}.pdf},
     * updates the Worker profile fields, writes an audit log entry, and notifies the Admin.
     *
     * @param workerUid  Firebase Auth UID of the Worker
     * @param file       the uploaded PDF (application/pdf, max 20 MB — validated by StorageService)
     * @param expiryDate the policy expiry date declared by the Worker
     * @throws ResponseStatusException 404 if the Worker is not found
     * @throws ResponseStatusException 400 if the expiry date is in the past
     */
    public void uploadInsuranceDoc(String workerUid, MultipartFile file, LocalDate expiryDate)
            throws InterruptedException, ExecutionException {

        // Guard: expiry date must be in the future.
        if (!expiryDate.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Insurance expiry date must be in the future");
        }

        User user = fetchUser(workerUid);
        String workerName = user.getName() != null ? user.getName() : workerUid;

        // Upload to Firebase Storage — StorageService validates MIME type and size.
        String docUrl = storageService.uploadInsuranceDoc(workerUid, file);

        // Update Worker profile.
        Map<String, Object> updates = new HashMap<>();
        updates.put("worker.insuranceDocUrl",        docUrl);
        updates.put("worker.insuranceStatus",         STATUS_PENDING_REVIEW);
        updates.put("worker.insurancePolicyExpiry",   expiryDate.toString()); // ISO-8601: YYYY-MM-DD
        updates.put("worker.insuranceDeclaredAt",     Timestamp.now());
        updates.put("updatedAt",                      Timestamp.now());

        auditLogService.write(workerUid, "INSURANCE_SUBMITTED",
                "worker", workerUid, null, STATUS_PENDING_REVIEW);

        firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();

        // Notify Admin for review.
        notificationService.notifyAdminInsuranceSubmitted(workerUid, workerName);

        log.info("Insurance doc uploaded and queued for review — workerUid={} expiry={}",
                workerUid, expiryDate);
    }

    // ── Admin verification ────────────────────────────────────────────────────

    /**
     * Records an Admin's decision on a submitted insurance document.
     *
     * <p>If approved: {@code insuranceStatus} is set to {@code VALID}.
     * <p>If rejected: {@code insuranceStatus} is reset to {@code NONE} and the
     * document URL is cleared (the Admin is declining to store the document).
     * The Worker's {@code isActive} flag is not changed here — activation/deactivation
     * is driven by background check and expiry logic.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @param approved  {@code true} to approve; {@code false} to reject
     * @param adminUid  Firebase Auth UID of the Admin making the decision
     * @throws ResponseStatusException 404 if the Worker is not found
     * @throws ResponseStatusException 409 if the Worker's insurance status is not PENDING_REVIEW
     */
    public void adminVerifyInsurance(String workerUid, boolean approved, String adminUid)
            throws InterruptedException, ExecutionException {

        User user = fetchUser(workerUid);
        WorkerProfile worker = user.getWorker();
        String currentStatus = worker.getInsuranceStatus();

        if (!STATUS_PENDING_REVIEW.equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Worker " + workerUid + " insurance is not in PENDING_REVIEW state "
                            + "(current: " + currentStatus + ")");
        }

        String newStatus = approved ? STATUS_VALID : STATUS_NONE;

        Map<String, Object> updates = new HashMap<>();
        updates.put("worker.insuranceStatus", newStatus);
        updates.put("updatedAt",              Timestamp.now());

        if (!approved) {
            // Clear the document URL — document rejected; Worker must re-upload.
            updates.put("worker.insuranceDocUrl", null);
        }

        auditLogService.write(adminUid, "INSURANCE_VERIFIED",
                "worker", workerUid, currentStatus,
                newStatus + " | adminUid: " + adminUid);

        firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();

        // Re-evaluate INSURED badge — awarded on approval, revoked on rejection.
        badgeService.evaluateBadges(workerUid);

        // Notify Worker of the outcome.
        if (approved) {
            notificationService.sendInsuranceApproved(workerUid);
        } else {
            notificationService.sendInsuranceRejected(workerUid);
        }

        log.info("Admin {} {} insurance for Worker {}", adminUid, newStatus, workerUid);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches a User document from Firestore.
     *
     * @throws ResponseStatusException 404 if the document does not exist
     * @throws ResponseStatusException 400 if the user has no Worker profile
     */
    private User fetchUser(String uid) throws InterruptedException, ExecutionException {
        var snap = firestore.collection(USERS_COLLECTION).document(uid).get().get();
        if (!snap.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Worker not found: " + uid);
        }
        User user = snap.toObject(User.class);
        if (user == null || user.getWorker() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User " + uid + " does not have a Worker profile");
        }
        return user;
    }
}
