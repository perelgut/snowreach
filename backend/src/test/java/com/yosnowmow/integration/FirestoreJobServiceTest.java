package com.yosnowmow.integration;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.GeoPoint;
import com.google.firebase.auth.FirebaseAuth;
import com.yosnowmow.dto.CreateJobRequest;
import com.yosnowmow.dto.CreateUserRequest;
import com.yosnowmow.exception.JobNotFoundException;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.GeocodingService;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.NotificationService;
import com.yosnowmow.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link JobService} against the Firestore emulator.
 *
 * <h3>Prerequisites</h3>
 * <pre>
 *   firebase emulators:start --only firestore
 *   mvn test -Dgroups=integration
 * </pre>
 * All tests skip automatically if the emulator is not reachable on
 * {@code localhost:8080}.
 *
 * <h3>What is mocked</h3>
 * <ul>
 *   <li>{@link FirebaseAuth} — skips custom-claims calls; no Auth emulator required</li>
 *   <li>{@link NotificationService} — skips SendGrid and FCM calls</li>
 *   <li>{@link GeocodingService} — skips Google Maps calls; returns a fixed Toronto
 *       coordinate by default; individual tests may override for failure paths</li>
 * </ul>
 *
 * {@link com.yosnowmow.service.AuditLogService} is intentionally <em>not</em> mocked —
 * it runs against the {@code demo-yosnowmow-audit} namespace in the same emulator
 * instance, giving end-to-end coverage of the audit write path.
 *
 * <h3>Isolation</h3>
 * {@link #setUpUser()} creates a unique requester document before each test.
 * {@link #cleanup()} deletes every user and job document touched during the test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("integration")
@DisplayName("JobService — Firestore emulator integration")
class FirestoreJobServiceTest {

    private static final GeoPoint TORONTO_COORDS = new GeoPoint(43.6532, -79.3832);
    private static final String   TEST_ADDRESS   = "123 Main St, Toronto, ON M5V 1A1";

    @Autowired private JobService  jobService;
    @Autowired private UserService userService;
    @Autowired private Firestore   firestore;

    @MockBean private FirebaseAuth       firebaseAuth;
    @MockBean private NotificationService notificationService;
    @MockBean private GeocodingService   geocodingService;

    /** UID of the requester created fresh for each test. */
    private String testUid;
    private AuthenticatedUser testCaller;

    /** Job IDs to delete in {@link #cleanup()}. */
    private final List<String> createdJobIds = new ArrayList<>();

    // ── Setup / Teardown ──────────────────────────────────────────────────────

    @BeforeAll
    static void requireEmulator() {
        assumeTrue(
                isPortOpen("localhost", 8080),
                "Skipping integration tests — Firestore emulator not reachable on localhost:8080. " +
                "Start it with: firebase emulators:start --only firestore"
        );
    }

    /**
     * Creates a unique requester user in Firestore before each test and sets the
     * default geocoding stub to return Toronto coordinates.
     */
    @BeforeEach
    void setUpUser() {
        testUid    = "test-job-req-" + UUID.randomUUID();
        testCaller = new AuthenticatedUser(testUid, testUid + "@test.com", List.of("requester"));
        userService.createUser(testCaller, validUserRequest());

        // Default geocoding response — individual tests may override for failure paths.
        when(geocodingService.geocode(anyString()))
                .thenReturn(new GeocodingService.GeocodeResult(TORONTO_COORDS, "google"));
    }

    /** Deletes all user and job documents written during the test. */
    @AfterEach
    void cleanup() throws Exception {
        firestore.collection("users").document(testUid).delete().get();
        for (String jobId : createdJobIds) {
            firestore.collection("jobs").document(jobId).delete().get();
        }
        createdJobIds.clear();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createJob: valid request → document written to Firestore with correct fields")
    void createJob_writesDocumentToFirestore() throws Exception {
        Job returned = createTestJob();

        // Returned object has correct shape.
        assertThat(returned.getJobId()).isNotBlank();
        assertThat(returned.getRequesterId()).isEqualTo(testUid);
        assertThat(returned.getStatus()).isEqualTo("REQUESTED");
        assertThat(returned.getScope()).containsExactly("driveway");
        assertThat(returned.getPropertyCoords()).isNotNull();
        assertThat(returned.getCreatedAt()).isNotNull();

        // Document actually exists in the emulator.
        DocumentSnapshot snap = firestore.collection("jobs")
                .document(returned.getJobId()).get().get();
        assertThat(snap.exists()).isTrue();
        assertThat(snap.getString("status")).isEqualTo("REQUESTED");
        assertThat(snap.getString("requesterId")).isEqualTo(testUid);
    }

    @Test
    @DisplayName("createJob: requester already has an active job → 409 Conflict")
    void createJob_requesterHasActiveJob_throws409() {
        // First job should succeed.
        createTestJob();

        // Second job for the same requester → 409.
        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> jobService.createJob(testCaller, validJobRequest())
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("createJob: invalid scope value → 422 Unprocessable Entity")
    void createJob_invalidScope_throws422() {
        CreateJobRequest req = validJobRequest();
        req.setScope(List.of("roof")); // not in {driveway, sidewalk, both}

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> jobService.createJob(testCaller, req)
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // Validation throws before geocoding or Firestore write.
    }

    @Test
    @DisplayName("createJob: geocoding fails → 422 Unprocessable Entity")
    void createJob_geocodingFails_throws422() {
        // Override the default stub to throw GeocodingException.
        when(geocodingService.geocode(anyString()))
                .thenThrow(new GeocodingService.GeocodingException(TEST_ADDRESS));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> jobService.createJob(testCaller, validJobRequest())
        );
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        // No job document should exist — nothing was written to Firestore.
    }

    @Test
    @DisplayName("getJob: existing document → returns Job with correct fields")
    void getJob_existingJob_returnsJob() {
        Job created = createTestJob();

        Job fetched = jobService.getJob(created.getJobId());

        assertThat(fetched.getJobId()).isEqualTo(created.getJobId());
        assertThat(fetched.getRequesterId()).isEqualTo(testUid);
        assertThat(fetched.getStatus()).isEqualTo("REQUESTED");
    }

    @Test
    @DisplayName("getJob: ID not in Firestore → JobNotFoundException")
    void getJob_nonExistentId_throwsJobNotFoundException() {
        assertThrows(
                JobNotFoundException.class,
                () -> jobService.getJob("does-not-exist-" + UUID.randomUUID())
        );
    }

    @Test
    @DisplayName("transitionStatus: REQUESTED → PENDING_DEPOSIT updates Firestore status field")
    void transitionStatus_updatesStatusInFirestore() throws Exception {
        Job job = createTestJob();

        jobService.transitionStatus(job.getJobId(), "PENDING_DEPOSIT", "stripe", null);

        DocumentSnapshot snap = firestore.collection("jobs")
                .document(job.getJobId()).get().get();
        assertThat(snap.getString("status")).isEqualTo("PENDING_DEPOSIT");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Calls {@link JobService#createJob} and registers the returned job ID for
     * cleanup in {@link #cleanup()}.
     */
    private Job createTestJob() {
        Job job = jobService.createJob(testCaller, validJobRequest());
        createdJobIds.add(job.getJobId());
        return job;
    }

    /**
     * Returns a minimal valid {@link CreateJobRequest} for a driveway clearing at
     * the test address.
     */
    private static CreateJobRequest validJobRequest() {
        CreateJobRequest req = new CreateJobRequest();
        req.setScope(List.of("driveway"));
        req.setPropertyAddressText(TEST_ADDRESS);
        return req;
    }

    /**
     * Returns a valid {@link CreateUserRequest} for a 34-year-old requester.
     */
    private static CreateUserRequest validUserRequest() {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("Job Test User");
        req.setDateOfBirth("1990-06-15");
        req.setTosVersion("1.0");
        req.setPrivacyPolicyVersion("1.0");
        req.setRoles(List.of("requester"));
        return req;
    }

    private static boolean isPortOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
