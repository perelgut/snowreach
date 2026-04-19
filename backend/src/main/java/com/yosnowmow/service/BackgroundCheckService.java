package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Manages the full lifecycle of Certn background checks for Workers (P3-01).
 *
 * <h3>Flow</h3>
 * <ol>
 *   <li>Worker calls {@code POST /api/users/{uid}/worker/background-check} (with consent).</li>
 *   <li>{@link #submitBackgroundCheck} sends the Worker's details to the Certn API and
 *       stores the returned order ID on the Worker profile ({@code status = SUBMITTED}).</li>
 *   <li>When Certn finishes the check it POSTs a webhook to
 *       {@code POST /webhooks/certn}.</li>
 *   <li>{@link #handleCertnWebhook} maps the Certn result to our status, updates
 *       the Worker profile, activates the account if CLEAR, queues admin review if
 *       CONSIDER, or suspends the account if SUSPENDED.</li>
 * </ol>
 *
 * <h3>Certn status mapping</h3>
 * <pre>
 *   Certn "PASS"   → backgroundCheckStatus = "CLEAR",     isActive = true
 *   Certn "REVIEW" → backgroundCheckStatus = "CONSIDER",  isActive unchanged (stays false)
 *   Certn "FAIL"   → backgroundCheckStatus = "SUSPENDED", isActive = false
 * </pre>
 *
 * <h3>Security note</h3>
 * The Certn webhook endpoint is unauthenticated (Certn has no Firebase token).
 * Integrity is verified by HMAC-SHA256 in {@link com.yosnowmow.controller.WebhookController}.
 */
@Service
public class BackgroundCheckService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundCheckService.class);

    private static final String USERS_COLLECTION       = "users";
    private static final String ADMIN_REVIEW_COLLECTION = "adminReviewQueue";

    /** Background check status constants (stored in Firestore). */
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_CLEAR      = "CLEAR";
    public static final String STATUS_CONSIDER   = "CONSIDER";
    public static final String STATUS_SUSPENDED  = "SUSPENDED";
    public static final String STATUS_REJECTED   = "REJECTED";

    private final Firestore           firestore;
    private final AuditLogService     auditLogService;
    private final NotificationService notificationService;
    private final BadgeService        badgeService;
    private final RestClient          certnRestClient;

    /**
     * Certn API base URL — injected from config.
     * Default points to the Certn sandbox for local development.
     */
    @Value("${yosnowmow.certn.api-url:https://api-sandbox.certn.co/hp/v1}")
    private String certnApiUrl;

    public BackgroundCheckService(Firestore firestore,
                                  AuditLogService auditLogService,
                                  NotificationService notificationService,
                                  BadgeService badgeService,
                                  @Value("${yosnowmow.certn.api-key:placeholder-set-in-P3-01}")
                                  String certnApiKey) {
        this.firestore           = firestore;
        this.auditLogService     = auditLogService;
        this.notificationService = notificationService;
        this.badgeService        = badgeService;

        // Build a RestClient with the Certn token injected as a default header.
        // RestClient is the non-reactive replacement for RestTemplate (Spring Boot 3.2+).
        this.certnRestClient = RestClient.builder()
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Token " + certnApiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ── Submit background check ───────────────────────────────────────────────

    /**
     * Submits a background check to the Certn API for the given Worker.
     *
     * <p>Reads the Worker's name and email from Firestore, POSTs to Certn's
     * {@code /applicants/} endpoint with the {@code criminal_record} package,
     * then stores the returned applicant / order ID on the Worker profile.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @throws ResponseStatusException 409 if a check is already in progress or complete
     */
    public void submitBackgroundCheck(String workerUid)
            throws InterruptedException, ExecutionException {

        User user = fetchUser(workerUid);
        WorkerProfile worker = user.getWorker();

        // Idempotency guard: only allow submission from "not_submitted" / null state.
        String currentStatus = worker.getBackgroundCheckStatus();
        if (STATUS_SUBMITTED.equals(currentStatus)
                || STATUS_CLEAR.equals(currentStatus)
                || STATUS_CONSIDER.equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Background check already in progress or complete (status: " + currentStatus + ")");
        }

        // Build the Certn applicant creation payload.
        Map<String, Object> payload = new HashMap<>();
        payload.put("first_name",     extractFirstName(user.getName()));
        payload.put("last_name",      extractLastName(user.getName()));
        payload.put("email",          user.getEmail());
        payload.put("package",        "criminal_record");

        // POST to Certn API.
        log.info("Submitting background check to Certn for Worker {}", workerUid);
        @SuppressWarnings("unchecked")
        Map<String, Object> certnResponse = (Map<String, Object>) certnRestClient
                .post()
                .uri(certnApiUrl + "/applicants/")
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (certnResponse == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Empty response from Certn API");
        }

        // Certn returns an 'id' field that serves as the order/applicant ID.
        String certnOrderId = String.valueOf(certnResponse.getOrDefault("id", ""));
        if (certnOrderId.isEmpty()) {
            log.error("Certn API response missing 'id' field: {}", certnResponse);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Certn API response did not include an order ID");
        }

        // Persist the order ID and updated status on the Worker document.
        Map<String, Object> updates = new HashMap<>();
        updates.put("worker.certnOrderId",          certnOrderId);
        updates.put("worker.backgroundCheckStatus", STATUS_SUBMITTED);
        updates.put("worker.backgroundCheckDate",   Timestamp.now());
        updates.put("updatedAt",                    Timestamp.now());

        auditLogService.write(workerUid, "BACKGROUND_CHECK_SUBMITTED",
                "worker", workerUid, currentStatus, STATUS_SUBMITTED);

        firestore.collection(USERS_COLLECTION).document(workerUid)
                .update(updates).get();

        log.info("Background check submitted for Worker {} — Certn order ID: {}",
                workerUid, certnOrderId);
    }

    // ── Handle Certn webhook ──────────────────────────────────────────────────

    /**
     * Processes a Certn webhook callback after a background check completes.
     *
     * <p>Looks up the Worker by {@code certnOrderId}, maps the Certn result string
     * to our internal status, updates the Worker profile, and sends notifications.
     *
     * <p>This method is idempotent — reprocessing the same webhook produces the
     * same result.
     *
     * @param certnOrderId  the applicant/order ID returned by Certn at submission time
     * @param certnResult   Certn's result string: {@code "PASS"}, {@code "REVIEW"}, or {@code "FAIL"}
     */
    public void handleCertnWebhook(String certnOrderId, String certnResult)
            throws InterruptedException, ExecutionException {

        // Look up the Worker by the Certn order ID stored on their profile.
        QuerySnapshot snap = firestore.collection(USERS_COLLECTION)
                .whereEqualTo("worker.certnOrderId", certnOrderId)
                .get().get();

        if (snap.isEmpty()) {
            log.warn("Certn webhook received for unknown orderId: {}", certnOrderId);
            return;
        }

        QueryDocumentSnapshot doc = snap.getDocuments().get(0);
        String workerUid = doc.getId();
        User user = doc.toObject(User.class);

        if (user == null || user.getWorker() == null) {
            log.error("Certn webhook: user document for uid {} could not be deserialized", workerUid);
            return;
        }

        String previousStatus = user.getWorker().getBackgroundCheckStatus();
        String newStatus       = mapCertnResult(certnResult);

        log.info("Certn webhook for Worker {} — result: {} → status: {}",
                workerUid, certnResult, newStatus);

        Map<String, Object> updates = new HashMap<>();
        updates.put("worker.backgroundCheckStatus", newStatus);
        updates.put("worker.backgroundCheckDate",   Timestamp.now());
        updates.put("updatedAt",                    Timestamp.now());

        auditLogService.write("certn-webhook", "BACKGROUND_CHECK_RESULT",
                "worker", workerUid, previousStatus, newStatus);

        switch (newStatus) {
            case STATUS_CLEAR -> {
                // Activate the Worker — they are now eligible for job dispatch.
                updates.put("worker.isActive", true);
                firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();
                // Award VERIFIED badge now that background check is CLEAR.
                badgeService.evaluateBadges(workerUid);
                notificationService.sendBackgroundCheckApproved(workerUid);
                log.info("Worker {} background check CLEAR — account activated", workerUid);
            }
            case STATUS_CONSIDER -> {
                // Flag for manual admin review; Worker stays inactive until Admin decides.
                firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();
                queueForAdminReview(workerUid, certnOrderId, certnResult);
                notificationService.notifyAdminBackgroundCheckReview(workerUid, certnOrderId);
                log.info("Worker {} background check CONSIDER — queued for Admin review", workerUid);
            }
            case STATUS_SUSPENDED -> {
                // Ensure the Worker is deactivated.
                updates.put("worker.isActive", false);
                firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();
                notificationService.sendBackgroundCheckFailed(workerUid);
                notificationService.notifyAdminBackgroundCheckFailed(workerUid, certnOrderId);
                log.info("Worker {} background check SUSPENDED — account deactivated", workerUid);
            }
            default -> log.warn("Unknown mapped status '{}' for Certn result '{}'", newStatus, certnResult);
        }
    }

    // ── Admin override (P3-02) ────────────────────────────────────────────────

    /**
     * Applies an Admin decision to a background check that is in the
     * {@code adminReviewQueue} (i.e., status is {@code CONSIDER}).
     *
     * <p>Decisions:
     * <ul>
     *   <li>{@code "APPROVED"} — sets status to {@code CLEAR}, activates the Worker account,
     *       sends an approval email.</li>
     *   <li>{@code "REJECTED"} — sets status to {@code REJECTED}, deactivates the Worker account,
     *       sends a rejection email.</li>
     * </ul>
     * The Worker is removed from the {@code adminReviewQueue} collection in both cases.
     * The decision and reason are written to the audit log.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @param decision  {@code "APPROVED"} or {@code "REJECTED"}
     * @param adminUid  Firebase Auth UID of the admin making the decision
     * @param reason    free-text reason for the decision (required for audit)
     * @throws ResponseStatusException 404 if the Worker is not found;
     *                                 409 if the Worker's status is not CONSIDER
     */
    public void adminOverride(String workerUid, String decision,
                              String adminUid, String reason)
            throws InterruptedException, ExecutionException {

        User user = fetchUser(workerUid);
        String currentStatus = user.getWorker().getBackgroundCheckStatus();

        // Only CONSIDER workers are in the review queue; reject others.
        if (!STATUS_CONSIDER.equals(currentStatus)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Worker " + workerUid + " is not in CONSIDER state (current: " + currentStatus + ")");
        }

        boolean approved = "APPROVED".equalsIgnoreCase(decision);
        String  newStatus = approved ? STATUS_CLEAR : STATUS_REJECTED;

        Map<String, Object> updates = new HashMap<>();
        updates.put("worker.backgroundCheckStatus", newStatus);
        updates.put("worker.backgroundCheckDate",   Timestamp.now());
        updates.put("worker.isActive",              approved);
        updates.put("updatedAt",                    Timestamp.now());

        // Audit before write.
        Map<String, Object> auditExtra = new HashMap<>();
        auditExtra.put("adminUid", adminUid);
        auditExtra.put("reason",   reason);
        auditLogService.write(adminUid, "BACKGROUND_CHECK_ADMIN_OVERRIDE",
                "worker", workerUid, currentStatus, newStatus + " | reason: " + reason);

        firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();

        // Remove from admin review queue.
        firestore.collection(ADMIN_REVIEW_COLLECTION).document(workerUid).delete().get();

        // Notify Worker.
        if (approved) {
            notificationService.sendBackgroundCheckApproved(workerUid);
        } else {
            notificationService.sendBackgroundCheckRejected(workerUid);
        }

        log.info("Admin {} {} background check for Worker {} — reason: {}",
                adminUid, newStatus, workerUid, reason);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Maps a Certn result string to our internal background check status.
     *
     * @param certnResult {@code "PASS"}, {@code "REVIEW"}, or {@code "FAIL"}
     * @return our internal status string
     */
    private static String mapCertnResult(String certnResult) {
        return switch (certnResult.toUpperCase()) {
            case "PASS"   -> STATUS_CLEAR;
            case "REVIEW" -> STATUS_CONSIDER;
            case "FAIL"   -> STATUS_SUSPENDED;
            default       -> {
                log.warn("Unrecognised Certn result '{}' — treating as CONSIDER", certnResult);
                yield STATUS_CONSIDER;
            }
        };
    }

    /**
     * Fetches a User document from Firestore.
     *
     * @throws ResponseStatusException 404 if the user document does not exist
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

    /**
     * Writes a document to the {@code adminReviewQueue} collection so the Admin
     * Dashboard can surface workers whose background checks require manual review.
     */
    private void queueForAdminReview(String workerUid, String certnOrderId, String certnResult)
            throws InterruptedException, ExecutionException {

        Map<String, Object> entry = new HashMap<>();
        entry.put("workerUid",    workerUid);
        entry.put("certnOrderId", certnOrderId);
        entry.put("certnResult",  certnResult);
        entry.put("status",       "PENDING_REVIEW");
        entry.put("createdAt",    Timestamp.now());

        firestore.collection(ADMIN_REVIEW_COLLECTION).document(workerUid)
                .set(entry).get();
    }

    /** Splits a full display name into a first name (everything before the first space). */
    private static String extractFirstName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        int space = fullName.indexOf(' ');
        return space == -1 ? fullName : fullName.substring(0, space);
    }

    /** Splits a full display name into a last name (everything after the first space). */
    private static String extractLastName(String fullName) {
        if (fullName == null || fullName.isBlank()) return "";
        int space = fullName.indexOf(' ');
        return space == -1 ? "" : fullName.substring(space + 1);
    }
}
