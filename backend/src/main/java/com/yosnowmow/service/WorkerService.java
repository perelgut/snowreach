package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.yosnowmow.dto.WorkerProfileRequest;
import com.yosnowmow.exception.UserNotFoundException;
import com.yosnowmow.model.Address;
import com.yosnowmow.model.PricingTier;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import com.yosnowmow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Business logic for activating and managing Worker profiles.
 *
 * Worker data is stored as a sub-object ({@code worker}) within the user's
 * Firestore document ({@code users/{uid}}), as specified in §3.1.
 *
 * Geocoding (converting {@code baseAddress} to {@code baseCoords}) is deferred
 * to P1-07 (GeocodingService).  Until then, {@code baseCoords} remains null and
 * the matching algorithm (P1-09) skips workers without coordinates.
 *
 * The "worker" role is added to the user's roles array and mirrored to the
 * Firebase custom claim on activation.
 */
@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private static final String USERS_COLLECTION = "users";

    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final UserService userService;

    public WorkerService(Firestore firestore, FirebaseAuth firebaseAuth, UserService userService) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
        this.userService = userService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Activates the Worker role for the given user and saves their profile.
     *
     * If the user already has a worker profile, this call is rejected with
     * HTTP 409.  Use {@link #updateWorkerProfile(String, WorkerProfileRequest)}
     * to update an existing profile.
     *
     * Required fields in the request: designation, baseAddressFullText,
     * serviceRadiusKm, bufferOptIn, tiers (1–3), hstRegistered.
     *
     * @param caller the authenticated caller
     * @param req    the worker profile payload
     * @return the full updated User document (includes the new worker sub-object)
     */
    public User activateWorker(AuthenticatedUser caller, WorkerProfileRequest req) {
        String uid = caller.uid();

        // 1. Confirm the user document exists
        User user = userService.getUser(uid);

        // 2. Guard against double-activation
        if (user.getWorker() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A Worker profile already exists. Use PATCH to update it.");
        }

        // 3. Validate required fields (not enforced by @NotNull on the DTO
        //    so the same DTO can be reused for PATCH where fields are optional)
        validateRequiredActivationFields(req);

        // 4. Validate tier consistency (outermost tier ≤ serviceRadiusKm)
        validateTiers(req);

        // 5. Validate HST number when registered
        if (Boolean.TRUE.equals(req.getHstRegistered()) && isBlank(req.getHstBusinessNumber())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "hstBusinessNumber is required when hstRegistered is true.");
        }

        // 6. Build the WorkerProfile sub-object
        WorkerProfile profile = buildInitialProfile(req);

        // 7. Ensure "worker" is in the roles list; update the user doc atomically
        List<String> updatedRoles = addWorkerRole(user.getRoles());
        Map<String, Object> updates = new HashMap<>();
        updates.put("worker", profile);
        updates.put("roles", updatedRoles);
        updates.put("updatedAt", Timestamp.now());
        writeUpdates(uid, updates);

        // 8. Mirror updated roles to Firebase custom claims
        setCustomClaims(uid, updatedRoles);

        log.info("Worker activated: uid={}", uid);
        return userService.getUser(uid);
    }

    /**
     * Applies a partial update to an existing Worker profile.
     *
     * Only non-null fields in the request are written; server-managed fields
     * (stripeConnectAccountId, rating, baseCoords, etc.) are never touched here.
     *
     * Status may be updated only to "available" or "unavailable".
     * "busy" is reserved for the dispatch system.
     *
     * @param uid the Firebase Auth UID of the Worker to update
     * @param req fields to update
     * @return the full updated User document
     */
    public User updateWorkerProfile(String uid, WorkerProfileRequest req) {
        User user = userService.getUser(uid);

        if (user.getWorker() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No Worker profile found. Use POST /api/users/me/worker to activate.");
        }

        // Validate tiers if provided
        if (req.getTiers() != null) {
            validateTiers(req);
        }

        // Validate HST number if registration status is being changed
        if (Boolean.TRUE.equals(req.getHstRegistered()) && isBlank(req.getHstBusinessNumber())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "hstBusinessNumber is required when hstRegistered is true.");
        }

        // Build partial update map using Firestore dot-notation for nested fields
        Map<String, Object> updates = new HashMap<>();

        if (req.getDesignation() != null)
            updates.put("worker.designation", req.getDesignation());

        if (req.getBaseAddressFullText() != null) {
            // Store address text; baseCoords will be populated by GeocodingService (P1-07)
            Address addr = new Address(req.getBaseAddressFullText());
            updates.put("worker.baseAddress", addr);
            // Clear coords so the geocoding service knows to re-geocode
            updates.put("worker.baseCoords", null);
            updates.put("worker.addressGeocodeMethod", null);
        }

        if (req.getServiceRadiusKm() != null)
            updates.put("worker.serviceRadiusKm", req.getServiceRadiusKm());

        if (req.getBufferOptIn() != null)
            updates.put("worker.bufferOptIn", req.getBufferOptIn());

        if (req.getTiers() != null)
            updates.put("worker.tiers", mapTiers(req.getTiers()));

        if (req.getHstRegistered() != null)
            updates.put("worker.hstRegistered", req.getHstRegistered());

        if (req.getHstBusinessNumber() != null)
            updates.put("worker.hstBusinessNumber", req.getHstBusinessNumber());

        if (req.getStatus() != null)
            updates.put("worker.status", req.getStatus());

        updates.put("updatedAt", Timestamp.now());

        writeUpdates(uid, updates);
        log.info("Worker profile updated: uid={} fields={}", uid, updates.keySet());
        return userService.getUser(uid);
    }

    // ── Package-private helpers (used by other services) ─────────────────────

    /**
     * Returns the User document for a Worker by UID.
     * Used by MatchingService, DispatchService, and RatingService.
     *
     * @throws UserNotFoundException if no document exists for this UID
     */
    public User getWorkerUser(String uid) {
        return userService.getUser(uid);
    }

    /**
     * Increments the Worker's activeJobCount by {@code delta} (positive to increment,
     * negative to decrement) and updates status to "busy" if activeJobCount > 0.
     *
     * Called by JobService when a job transitions to CONFIRMED or exits IN_PROGRESS.
     */
    public void adjustActiveJobCount(String uid, int delta) {
        try {
            firestore.runTransaction(tx -> {
                DocumentReference ref = firestore.collection(USERS_COLLECTION).document(uid);
                DocumentSnapshot snap = tx.get(ref).get();
                if (!snap.exists()) {
                    throw new UserNotFoundException(uid);
                }
                Long current = snap.getLong("worker.activeJobCount");
                long updated = (current == null ? 0 : current) + delta;
                if (updated < 0) updated = 0;

                tx.update(ref, "worker.activeJobCount", updated);
                // "busy" when actively working; "available" when count drops to 0
                tx.update(ref, "worker.status", updated > 0 ? "busy" : "available");
                tx.update(ref, "updatedAt", Timestamp.now());
                return null;
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to adjust activeJobCount for uid {}: {}", uid, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to update worker job count");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Validates that all fields required for initial activation are present.
     */
    private void validateRequiredActivationFields(WorkerProfileRequest req) {
        List<String> missing = new ArrayList<>();
        if (isBlank(req.getDesignation()))           missing.add("designation");
        if (isBlank(req.getBaseAddressFullText()))   missing.add("baseAddressFullText");
        if (req.getServiceRadiusKm() == null)        missing.add("serviceRadiusKm");
        if (req.getBufferOptIn() == null)            missing.add("bufferOptIn");
        if (req.getTiers() == null || req.getTiers().isEmpty()) missing.add("tiers");
        if (req.getHstRegistered() == null)          missing.add("hstRegistered");

        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Missing required fields for Worker activation: " + missing);
        }
    }

    /**
     * Validates that tier maxDistanceKm values are ordered ascending and that
     * the outermost tier does not exceed serviceRadiusKm.
     */
    private void validateTiers(WorkerProfileRequest req) {
        List<WorkerProfileRequest.TierDto> tiers = req.getTiers();
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).getMaxDistanceKm() <= tiers.get(i - 1).getMaxDistanceKm()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Tier maxDistanceKm values must be in strictly ascending order.");
            }
        }
        if (req.getServiceRadiusKm() != null) {
            double outermost = tiers.get(tiers.size() - 1).getMaxDistanceKm();
            if (outermost > req.getServiceRadiusKm()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Outermost tier maxDistanceKm (" + outermost
                                + ") must not exceed serviceRadiusKm (" + req.getServiceRadiusKm() + ").");
            }
        }
    }

    /** Builds a fresh WorkerProfile with all server-side defaults. */
    private WorkerProfile buildInitialProfile(WorkerProfileRequest req) {
        WorkerProfile p = new WorkerProfile();
        p.setDesignation(req.getDesignation());
        p.setBaseAddress(new Address(req.getBaseAddressFullText()));
        // baseCoords left null — GeocodingService (P1-07) will populate it
        p.setServiceRadiusKm(req.getServiceRadiusKm());
        p.setBufferOptIn(req.getBufferOptIn());
        p.setTiers(mapTiers(req.getTiers()));
        p.setHstRegistered(req.getHstRegistered());
        if (!isBlank(req.getHstBusinessNumber())) {
            p.setHstBusinessNumber(req.getHstBusinessNumber());
        }
        p.setReferredByUserId(req.getReferredByUserId());

        // Server-set defaults
        p.setStripeConnectStatus("not_connected");
        p.setStatus("available");
        p.setConsecutiveNonResponses(0);
        p.setActiveJobCount(0);
        p.setCapacityMax(1);
        p.setEarlyAdopter(false);
        p.setEarlyAdopterCommissionJobsRemaining(0);
        p.setFoundingWorker(false);
        p.setReferralCode(generateReferralCode());
        p.setPhoneVerifiedForJobs(false);
        p.setRatingCount(0);
        p.setCompletedJobCount(0);
        p.setCannotCompleteCount90d(0);
        p.setBackgroundCheckStatus("not_submitted");
        return p;
    }

    /** Converts DTO tier list to model tier list. */
    private List<PricingTier> mapTiers(List<WorkerProfileRequest.TierDto> dtos) {
        return dtos.stream()
                .map(dto -> new PricingTier(dto.getMaxDistanceKm(), dto.getPriceCAD()))
                .collect(Collectors.toList());
    }

    /**
     * Returns a new roles list that includes "worker".
     * The original list is not mutated.
     */
    private List<String> addWorkerRole(List<String> existing) {
        List<String> updated = new ArrayList<>(existing != null ? existing : List.of());
        if (!updated.contains("worker")) {
            updated.add("worker");
        }
        return updated;
    }

    /** Writes a field-level update map to the user document. */
    private void writeUpdates(String uid, Map<String, Object> updates) {
        try {
            firestore.collection(USERS_COLLECTION).document(uid).update(updates).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error updating worker profile for uid {}: {}", uid, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to save worker profile");
        }
    }

    /** Mirrors the updated roles list to Firebase custom claims (non-fatal on failure). */
    private void setCustomClaims(String uid, List<String> roles) {
        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", roles);
            firebaseAuth.setCustomUserClaims(uid, claims);
        } catch (FirebaseAuthException e) {
            log.error("Failed to set custom claims for uid {}: {}", uid, e.getMessage(), e);
        }
    }

    /**
     * Generates a short, unique referral code for the Worker.
     * Uses the first 8 characters of a UUID (uppercase).
     */
    private String generateReferralCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
