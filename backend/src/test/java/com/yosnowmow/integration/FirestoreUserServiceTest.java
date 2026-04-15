package com.yosnowmow.integration;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.dto.CreateUserRequest;
import com.yosnowmow.exception.UserNotFoundException;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.NotificationService;
import com.yosnowmow.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for {@link UserService} against the Firestore emulator.
 *
 * <h3>Prerequisites</h3>
 * <pre>
 *   firebase emulators:start --only firestore
 *   mvn test
 * </pre>
 * All tests are tagged {@code @Tag("integration")} and are skipped automatically
 * (via {@code assumeTrue}) if the Firestore emulator is not reachable on
 * {@code localhost:8080}.
 *
 * <h3>What is mocked</h3>
 * <ul>
 *   <li>{@link FirebaseAuth} — skips {@code setCustomUserClaims()}; no Auth emulator required</li>
 *   <li>{@link NotificationService} — skips SendGrid welcome emails during registration</li>
 * </ul>
 *
 * <h3>Isolation</h3>
 * Each test generates a UUID-based UID.  {@link #cleanup()} deletes every
 * document created during the test run so tests stay independent regardless
 * of execution order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("UserService — Firestore emulator integration")
class FirestoreUserServiceTest {

    /** Firestore collection that UserService writes to. */
    private static final String USERS_COLLECTION = "users";

    @Autowired
    private UserService userService;

    @Autowired
    private Firestore firestore;

    /** Mock out Firebase Auth custom-claims calls — no Auth emulator needed. */
    @MockBean
    private FirebaseAuth firebaseAuth;

    /** Mock out welcome emails — no SendGrid calls during tests. */
    @MockBean
    private NotificationService notificationService;

    /** UIDs of documents created during the current test, collected for cleanup. */
    private final List<String> createdUids = new ArrayList<>();

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    /**
     * Skip the entire test class gracefully if the Firestore emulator is not running.
     * The port check happens before the first test method; if it fails every test is
     * reported as skipped (not failed).
     */
    @BeforeAll
    static void requireEmulator() {
        assumeTrue(
                isPortOpen("localhost", 8080),
                "Skipping integration tests — Firestore emulator not reachable on localhost:8080. " +
                "Start it with: firebase emulators:start --only firestore"
        );
    }

    /** Delete every document created during the test to keep the emulator clean. */
    @AfterEach
    void cleanup() throws Exception {
        for (String uid : createdUids) {
            // delete() on a non-existent document is a no-op — safe even if the
            // test threw before writing (e.g. under-age or forbidden-role tests).
            firestore.collection(USERS_COLLECTION).document(uid).delete().get();
        }
        createdUids.clear();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createUser: valid requester → document written to Firestore with correct fields")
    void createUser_writesDocumentToFirestore() throws Exception {
        String uid = newUid();
        User returned = userService.createUser(caller(uid), validRequest());

        // Returned object has correct shape
        assertThat(returned.getUid()).isEqualTo(uid);
        assertThat(returned.getName()).isEqualTo("Test User");
        assertThat(returned.getEmail()).isEqualTo(uid + "@test.com");
        assertThat(returned.getAccountStatus()).isEqualTo("active");
        assertThat(returned.getRoles()).containsExactly("requester");
        assertThat(returned.getCreatedAt()).isNotNull();

        // Document actually exists in the emulator
        DocumentSnapshot snapshot = firestore.collection(USERS_COLLECTION).document(uid).get().get();
        assertThat(snapshot.exists()).isTrue();
        assertThat(snapshot.getString("name")).isEqualTo("Test User");
        assertThat(snapshot.getString("accountStatus")).isEqualTo("active");
    }

    @Test
    @DisplayName("createUser: duplicate UID → 409 Conflict")
    void createUser_duplicateUid_throws409() {
        String uid = newUid();
        AuthenticatedUser caller = caller(uid);
        userService.createUser(caller, validRequest());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUser(caller, validRequest())
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("getUser: existing document → returns User with correct fields")
    void getUser_existingDocument_returnsUser() {
        String uid = newUid();
        userService.createUser(caller(uid), validRequest());

        User fetched = userService.getUser(uid);

        assertThat(fetched.getUid()).isEqualTo(uid);
        assertThat(fetched.getName()).isEqualTo("Test User");
        assertThat(fetched.getRoles()).containsExactly("requester");
    }

    @Test
    @DisplayName("getUser: UID not in Firestore → UserNotFoundException")
    void getUser_nonExistingUid_throwsUserNotFoundException() {
        // Use a UID that was never written — no cleanup needed
        assertThrows(
                UserNotFoundException.class,
                () -> userService.getUser("uid-that-does-not-exist-" + UUID.randomUUID())
        );
    }

    @Test
    @DisplayName("createUser: date of birth < 18 years ago → 422 Unprocessable Entity")
    void createUser_underAge_throws422() {
        String uid = newUid();
        CreateUserRequest req = validRequest();
        req.setDateOfBirth("2015-01-01"); // clearly under 18

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUser(caller(uid), req)
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // Validation throws before any Firestore write — cleanup is a no-op
    }

    @Test
    @DisplayName("createUser: admin role self-assigned → 403 Forbidden")
    void createUser_adminRoleSelfAssigned_throws403() {
        String uid = newUid();
        CreateUserRequest req = validRequest();
        req.setRoles(List.of("admin"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> userService.createUser(caller(uid), req)
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        // Validation throws before any Firestore write — cleanup is a no-op
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Generates a unique test UID and registers it for cleanup in {@link #cleanup()}.
     */
    private String newUid() {
        String uid = "test-" + UUID.randomUUID();
        createdUids.add(uid);
        return uid;
    }

    /**
     * Builds an {@link AuthenticatedUser} with the given UID (simulates a verified
     * Firebase ID token with the "requester" role).
     */
    private static AuthenticatedUser caller(String uid) {
        return new AuthenticatedUser(uid, uid + "@test.com", List.of("requester"));
    }

    /**
     * Returns a fully populated, valid {@link CreateUserRequest} for a 34-year-old requester.
     * Tests that need different values should call {@code validRequest()} and override fields.
     */
    private static CreateUserRequest validRequest() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("Test User");
        req.setDateOfBirth("1990-06-15");
        req.setTosVersion("1.0");
        req.setPrivacyPolicyVersion("1.0");
        req.setRoles(List.of("requester"));
        return req;
    }

    /**
     * Returns {@code true} if the given host:port accepts a TCP connection within 500 ms.
     * Used to detect whether the Firebase emulator is running before any test runs.
     */
    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
