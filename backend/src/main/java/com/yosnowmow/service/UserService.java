package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.yosnowmow.dto.CreateUserRequest;
import com.yosnowmow.dto.UpdateUserRequest;
import com.yosnowmow.exception.UserNotFoundException;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Period;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Business logic for user registration and profile management.
 *
 * All Firestore writes go through this service — the React client
 * never writes operational data directly (architecture rule §1).
 *
 * Responsibilities (P1-05):
 *   - Create a new user document in {@code users/{uid}}
 *   - Mirror roles to the Firebase Auth custom claim "roles"
 *   - Fetch and patch user profile documents
 *
 * Role rules:
 *   - Self-registration may only assign "requester" and/or "worker"
 *   - "admin" can only be granted by an existing admin
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    /** Firestore collection name for user documents. */
    private static final String USERS_COLLECTION = "users";

    /** Minimum age to register (Ontario law — must be 18+). */
    private static final int MINIMUM_AGE_YEARS = 18;

    /** Roles that a user is allowed to self-assign at registration. */
    private static final Set<String> SELF_ASSIGNABLE_ROLES = Set.of("requester", "worker");

    private final Firestore firestore;
    private final FirebaseAuth firebaseAuth;
    private final NotificationService notificationService;

    public UserService(Firestore firestore,
                       FirebaseAuth firebaseAuth,
                       NotificationService notificationService) {
        this.firestore = firestore;
        this.firebaseAuth = firebaseAuth;
        this.notificationService = notificationService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Registers a new user by creating a Firestore document and setting
     * Firebase custom claims.
     *
     * Preconditions:
     *   - The caller's Firebase ID token has already been verified by
     *     FirebaseTokenFilter (UID is trusted).
     *   - No existing document may exist for this UID (enforced with a
     *     Firestore precondition — returns 409 Conflict if already registered).
     *
     * @param caller the authenticated caller (uid comes from the token)
     * @param req    the registration payload
     * @return the newly created User document
     */
    public User createUser(AuthenticatedUser caller, CreateUserRequest req) {
        String uid = caller.uid();

        // 1. Validate age (must be 18+)
        validateAge(req.getDateOfBirth(), uid);

        // 2. Validate that requested roles are all self-assignable
        validateSelfAssignableRoles(req.getRoles(), uid);

        // 3. Check that this UID has not already registered
        DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(uid);
        ensureNoExistingDocument(docRef, uid);

        // 4. Build the user document
        Timestamp now = Timestamp.now();
        User user = new User();
        user.setUid(uid);
        user.setEmail(caller.email());
        user.setName(req.getName());
        user.setDateOfBirth(req.getDateOfBirth());
        user.setAgeVerifiedAt(now);
        user.setTosVersion(req.getTosVersion());
        user.setTosAcceptedAt(now);
        user.setPrivacyPolicyVersion(req.getPrivacyPolicyVersion());
        user.setPrivacyPolicyAcceptedAt(now);
        user.setRoles(req.getRoles());
        user.setPhoneNumber(req.getPhoneNumber());
        user.setAccountStatus("active");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);

        // 5. Write to Firestore
        writeToFirestore(docRef, user, uid);

        // 6. Mirror roles to Firebase custom claims so the ID token carries them
        setCustomClaims(uid, req.getRoles());

        log.info("User registered: uid={} roles={}", uid, req.getRoles());

        // Send welcome email (P1-17). Tone based on primary role.
        String primaryRole = req.getRoles().contains("worker") ? "WORKER" : "REQUESTER";
        notificationService.sendWelcomeEmail(uid, req.getName(), primaryRole);

        return user;
    }

    /**
     * Returns the user document for the given ID.
     *
     * Access control (enforced in the controller):
     *   - Users may fetch their own profile.
     *   - Admins may fetch any profile.
     *
     * @param userId the Firebase Auth UID
     * @return the User document
     * @throws UserNotFoundException if no document exists for this UID
     */
    public User getUser(String userId) {
        DocumentReference docRef = firestore.collection(USERS_COLLECTION).document(userId);
        try {
            DocumentSnapshot snapshot = docRef.get().get();
            if (!snapshot.exists()) {
                throw new UserNotFoundException(userId);
            }
            User user = snapshot.toObject(User.class);
            if (user == null) {
                throw new UserNotFoundException(userId);
            }
            return user;
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error fetching user {}: {}", userId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch user profile");
        }
    }

    /**
     * Applies a partial update to a user's profile.
     *
     * Only non-null fields in the request are written.
     * Role changes and account status changes are out of scope here
     * (those require separate admin operations).
     *
     * Access control (enforced in the controller):
     *   - Users may update their own profile only.
     *   - Admins may update any profile.
     *
     * @param userId the Firebase Auth UID of the user to update
     * @param req    fields to update (null fields are ignored)
     * @return the updated User document
     */
    public User updateUser(String userId, UpdateUserRequest req) {
        // Confirm the user exists before patching
        getUser(userId);

        Map<String, Object> updates = new HashMap<>();
        if (req.getName() != null)        updates.put("name",        req.getName());
        if (req.getDateOfBirth() != null) updates.put("dateOfBirth", req.getDateOfBirth());
        if (req.getPhoneNumber() != null) updates.put("phoneNumber", req.getPhoneNumber());

        // Always refresh updatedAt
        updates.put("updatedAt", Timestamp.now());

        if (updates.size() > 1) { // more than just updatedAt
            try {
                firestore.collection(USERS_COLLECTION)
                        .document(userId)
                        .update(updates)
                        .get();
                log.info("User updated: uid={} fields={}", userId, updates.keySet());
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                log.error("Firestore error updating user {}: {}", userId, e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to update user profile");
            }
        }

        return getUser(userId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Validates that the caller is at least {@value #MINIMUM_AGE_YEARS} years old.
     *
     * @throws ResponseStatusException HTTP 422 if under-age
     */
    private void validateAge(String dateOfBirth, String uid) {
        LocalDate dob = LocalDate.parse(dateOfBirth);
        int age = Period.between(dob, LocalDate.now()).getYears();
        if (age < MINIMUM_AGE_YEARS) {
            log.warn("Under-age registration attempt: uid={} dob={}", uid, dateOfBirth);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "You must be at least " + MINIMUM_AGE_YEARS + " years old to register.");
        }
    }

    /**
     * Ensures all requested roles are in the self-assignable set.
     * Users cannot self-assign "admin".
     *
     * @throws ResponseStatusException HTTP 403 if an illegal role is requested
     */
    private void validateSelfAssignableRoles(List<String> roles, String uid) {
        for (String role : roles) {
            if (!SELF_ASSIGNABLE_ROLES.contains(role)) {
                log.warn("Illegal role requested: uid={} role={}", uid, role);
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Role '" + role + "' cannot be self-assigned.");
            }
        }
    }

    /**
     * Fails with HTTP 409 if a user document already exists for this UID.
     * Prevents double-registration (e.g. from network retries).
     */
    private void ensureNoExistingDocument(DocumentReference docRef, String uid) {
        try {
            DocumentSnapshot snapshot = docRef.get().get();
            if (snapshot.exists()) {
                log.warn("Duplicate registration attempt: uid={}", uid);
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "A user profile already exists for this account.");
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error checking existence for uid {}: {}", uid, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to check existing user profile");
        }
    }

    /** Writes the User object to Firestore, wrapping checked exceptions. */
    private void writeToFirestore(DocumentReference docRef, User user, String uid) {
        try {
            docRef.set(user).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Firestore error creating user {}: {}", uid, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create user profile");
        }
    }

    /**
     * Sets the "roles" Firebase custom claim so the client ID token carries role
     * information after the next token refresh (up to 1-hour lag).
     *
     * @see <a href="https://firebase.google.com/docs/auth/admin/custom-claims">Firebase custom claims</a>
     */
    private void setCustomClaims(String uid, List<String> roles) {
        try {
            Map<String, Object> claims = new HashMap<>();
            claims.put("roles", roles);
            firebaseAuth.setCustomUserClaims(uid, claims);
            log.debug("Custom claims set: uid={} roles={}", uid, roles);
        } catch (FirebaseAuthException e) {
            // Non-fatal — log and continue.  The token filter has a graceful fallback
            // (empty roles) so the user can still authenticate; roles will appear on
            // the next token refresh.
            log.error("Failed to set custom claims for uid {}: {}", uid, e.getMessage(), e);
        }
    }
}
