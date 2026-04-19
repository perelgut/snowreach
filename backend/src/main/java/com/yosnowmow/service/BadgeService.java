package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Manages the Worker trust badge lifecycle (P3-04).
 *
 * <h3>Badge types and eligibility</h3>
 * <pre>
 *   VERIFIED    — backgroundCheckStatus == "CLEAR"
 *   INSURED     — insuranceStatus == "VALID" or "EXPIRING_SOON"
 *   TOP_RATED   — rating >= 4.8 AND completedJobCount >= 25
 *   EXPERIENCED — completedJobCount >= 100
 * </pre>
 *
 * <h3>Firestore schema</h3>
 * Badges are stored in a subcollection: {@code users/{uid}/badges/{badgeType}}
 * where {@code badgeType} is one of the four type strings above.
 * Each document has:
 * <ul>
 *   <li>{@code badgeId}           — same as the document ID (badge type string)</li>
 *   <li>{@code awardedAt}         — Timestamp when the badge was first awarded</li>
 *   <li>{@code awardedBySystem}   — true when auto-awarded; false when admin-granted</li>
 *   <li>{@code awardedByAdminUid} — UID of the granting admin (null for system awards)</li>
 *   <li>{@code isActive}          — true while the badge is currently displayed</li>
 *   <li>{@code revokedAt}         — Timestamp when last revoked (null if never)</li>
 *   <li>{@code revokedByAdminUid} — UID of the revoking admin (null if auto-revoked or never)</li>
 *   <li>{@code revokedReason}     — admin's reason text (null if auto-revoked or never)</li>
 * </ul>
 *
 * <h3>Call sites for evaluateBadges</h3>
 * <ul>
 *   <li>{@link RatingService} — after updating Worker's average rating</li>
 *   <li>{@link BackgroundCheckService} — after a Certn webhook sets status to CLEAR</li>
 *   <li>{@link InsuranceService} — after Admin approves an insurance document</li>
 *   <li>{@link com.yosnowmow.scheduler.InsuranceRenewalJob} — after marking insurance EXPIRED</li>
 * </ul>
 */
@Service
public class BadgeService {

    private static final Logger log = LoggerFactory.getLogger(BadgeService.class);

    public static final String BADGE_VERIFIED    = "VERIFIED";
    public static final String BADGE_INSURED     = "INSURED";
    public static final String BADGE_TOP_RATED   = "TOP_RATED";
    public static final String BADGE_EXPERIENCED = "EXPERIENCED";

    public static final Set<String> ALL_BADGE_TYPES =
            Set.of(BADGE_VERIFIED, BADGE_INSURED, BADGE_TOP_RATED, BADGE_EXPERIENCED);

    private static final String USERS_COLLECTION  = "users";
    private static final String BADGES_COLLECTION = "badges";

    private final Firestore       firestore;
    private final AuditLogService auditLogService;

    public BadgeService(Firestore firestore, AuditLogService auditLogService) {
        this.firestore       = firestore;
        this.auditLogService = auditLogService;
    }

    // ── Auto-evaluation ───────────────────────────────────────────────────────

    /**
     * Re-evaluates all four badge types for a Worker and awards or revokes badges
     * based on the current state of their profile.
     *
     * <p>This method is idempotent — calling it multiple times produces the same result.
     * It is called automatically from {@link RatingService}, {@link BackgroundCheckService},
     * and {@link InsuranceService} whenever a relevant field changes.
     *
     * @param workerUid Firebase Auth UID of the Worker
     */
    public void evaluateBadges(String workerUid) {
        try {
            var snap = firestore.collection(USERS_COLLECTION).document(workerUid).get().get();
            if (!snap.exists()) {
                log.warn("evaluateBadges: Worker {} not found — skipping", workerUid);
                return;
            }
            User user = snap.toObject(User.class);
            if (user == null || user.getWorker() == null) {
                log.warn("evaluateBadges: Worker {} has no worker profile — skipping", workerUid);
                return;
            }
            WorkerProfile worker = user.getWorker();

            // Evaluate each badge type against current profile state.
            processBadge(workerUid, BADGE_VERIFIED,    isVerifiedEligible(worker));
            processBadge(workerUid, BADGE_INSURED,     isInsuredEligible(worker));
            processBadge(workerUid, BADGE_TOP_RATED,   isTopRatedEligible(worker));
            processBadge(workerUid, BADGE_EXPERIENCED, isExperiencedEligible(worker));

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("evaluateBadges failed for Worker {}: {}", workerUid, e.getMessage(), e);
        }
    }

    // ── Admin overrides ───────────────────────────────────────────────────────

    /**
     * Manually grants a badge to a Worker, bypassing eligibility checks.
     *
     * <p>The badge document is created (or re-activated) with
     * {@code awardedBySystem = false} and {@code awardedByAdminUid = adminUid}.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @param badgeType one of the {@code BADGE_*} constants
     * @param adminUid  Firebase Auth UID of the granting admin
     * @throws ResponseStatusException 400 if {@code badgeType} is not a valid badge type
     */
    public void adminGrantBadge(String workerUid, String badgeType, String adminUid)
            throws InterruptedException, ExecutionException {

        validateBadgeType(badgeType);

        DocumentReference ref = badgeRef(workerUid, badgeType);
        DocumentSnapshot existing = ref.get().get();

        if (existing.exists() && Boolean.TRUE.equals(existing.getBoolean("isActive"))) {
            log.info("Badge {} for Worker {} already active — no-op grant", badgeType, workerUid);
            return;
        }

        Map<String, Object> doc = buildBadgeDoc(badgeType, false, adminUid);
        ref.set(doc).get();

        auditLogService.write(adminUid, "BADGE_GRANTED",
                "worker", workerUid, "inactive", badgeType + " ACTIVE");

        log.info("Admin {} granted badge {} to Worker {}", adminUid, badgeType, workerUid);
    }

    /**
     * Manually revokes an active badge from a Worker.
     *
     * <p>The badge document is updated with {@code isActive = false},
     * {@code revokedAt}, {@code revokedByAdminUid}, and {@code revokedReason}.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @param badgeType one of the {@code BADGE_*} constants
     * @param adminUid  Firebase Auth UID of the revoking admin
     * @param reason    mandatory reason text written to the audit log and badge document
     * @throws ResponseStatusException 400 if {@code badgeType} is invalid
     * @throws ResponseStatusException 404 if no badge document exists for this Worker
     */
    public void adminRevokeBadge(String workerUid, String badgeType,
                                  String adminUid, String reason)
            throws InterruptedException, ExecutionException {

        validateBadgeType(badgeType);

        DocumentReference ref = badgeRef(workerUid, badgeType);
        DocumentSnapshot existing = ref.get().get();

        if (!existing.exists() || !Boolean.TRUE.equals(existing.getBoolean("isActive"))) {
            log.info("Badge {} for Worker {} already inactive — no-op revoke", badgeType, workerUid);
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("isActive",          false);
        updates.put("revokedAt",         Timestamp.now());
        updates.put("revokedByAdminUid", adminUid);
        updates.put("revokedReason",     reason);
        ref.update(updates).get();

        auditLogService.write(adminUid, "BADGE_REVOKED",
                "worker", workerUid, badgeType + " ACTIVE",
                badgeType + " INACTIVE | reason: " + reason);

        log.info("Admin {} revoked badge {} from Worker {} — reason: {}",
                adminUid, badgeType, workerUid, reason);
    }

    // ── Badge query ───────────────────────────────────────────────────────────

    /**
     * Returns all active badge documents for a Worker as a list of key-value maps.
     *
     * @param workerUid Firebase Auth UID of the Worker
     * @return list of active badge documents; empty list if none
     */
    public List<Map<String, Object>> getActiveBadges(String workerUid)
            throws InterruptedException, ExecutionException {

        var snap = firestore.collection(USERS_COLLECTION)
                .document(workerUid)
                .collection(BADGES_COLLECTION)
                .whereEqualTo("isActive", true)
                .get().get();

        List<Map<String, Object>> badges = new ArrayList<>();
        for (var doc : snap.getDocuments()) {
            badges.add(doc.getData());
        }
        return badges;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Core award/revoke logic for a single badge type.
     * Creates or re-activates the badge if the Worker is newly eligible;
     * deactivates it if they no longer qualify.
     */
    private void processBadge(String workerUid, String badgeType, boolean eligible)
            throws InterruptedException, ExecutionException {

        DocumentReference ref = badgeRef(workerUid, badgeType);
        DocumentSnapshot  doc = ref.get().get();
        boolean currentlyActive = doc.exists() && Boolean.TRUE.equals(doc.getBoolean("isActive"));

        if (eligible && !currentlyActive) {
            // Award the badge.
            ref.set(buildBadgeDoc(badgeType, true, null)).get();
            auditLogService.write("system", "BADGE_AWARDED",
                    "worker", workerUid, "inactive", badgeType + " ACTIVE");
            log.info("Badge {} awarded to Worker {} (system evaluation)", badgeType, workerUid);

        } else if (!eligible && currentlyActive) {
            // Revoke the badge (system-triggered, no admin reason).
            Map<String, Object> updates = new HashMap<>();
            updates.put("isActive",  false);
            updates.put("revokedAt", Timestamp.now());
            ref.update(updates).get();
            auditLogService.write("system", "BADGE_REVOKED",
                    "worker", workerUid, badgeType + " ACTIVE", badgeType + " INACTIVE (auto)");
            log.info("Badge {} revoked from Worker {} (eligibility lost)", badgeType, workerUid);
        }
        // If state didn't change, nothing to write.
    }

    /** Builds a new badge document for creation/reactivation. */
    private static Map<String, Object> buildBadgeDoc(String badgeType,
                                                      boolean awardedBySystem,
                                                      String awardedByAdminUid) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("badgeId",           badgeType);
        doc.put("awardedAt",         Timestamp.now());
        doc.put("awardedBySystem",   awardedBySystem);
        doc.put("awardedByAdminUid", awardedByAdminUid);
        doc.put("isActive",          true);
        doc.put("revokedAt",         null);
        doc.put("revokedByAdminUid", null);
        doc.put("revokedReason",     null);
        return doc;
    }

    private DocumentReference badgeRef(String workerUid, String badgeType) {
        return firestore.collection(USERS_COLLECTION)
                .document(workerUid)
                .collection(BADGES_COLLECTION)
                .document(badgeType);
    }

    private static void validateBadgeType(String badgeType) {
        if (!ALL_BADGE_TYPES.contains(badgeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown badge type: '" + badgeType + "'. Valid types: " + ALL_BADGE_TYPES);
        }
    }

    // ── Eligibility predicates ────────────────────────────────────────────────

    private static boolean isVerifiedEligible(WorkerProfile w) {
        return "CLEAR".equals(w.getBackgroundCheckStatus());
    }

    private static boolean isInsuredEligible(WorkerProfile w) {
        String s = w.getInsuranceStatus();
        return InsuranceService.STATUS_VALID.equals(s)
                || InsuranceService.STATUS_EXPIRING_SOON.equals(s);
    }

    private static boolean isTopRatedEligible(WorkerProfile w) {
        Double rating = w.getRating();
        return rating != null && rating >= 4.8 && w.getCompletedJobCount() >= 25;
    }

    private static boolean isExperiencedEligible(WorkerProfile w) {
        return w.getCompletedJobCount() >= 100;
    }
}
