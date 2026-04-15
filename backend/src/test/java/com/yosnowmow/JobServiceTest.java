package com.yosnowmow;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.GeoPoint;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import org.mockito.Answers;
import com.yosnowmow.dto.CreateJobRequest;
import com.yosnowmow.exception.InvalidTransitionException;
import com.yosnowmow.model.Address;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.AuditLogService;
import com.yosnowmow.service.GeocodingService;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.NotificationService;
import com.yosnowmow.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.quartz.Scheduler;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JobService} — covering {@code createJob()} and {@code cancelJob()}.
 *
 * <p>Firestore is completely mocked; these tests run without a database or emulator.
 * External services (GeocodingService, NotificationService, AuditLogService)
 * are Mockito mocks that record invocations and return no-op defaults.
 *
 * <p>{@code LENIENT} strictness is used because the shared {@code @BeforeEach}
 * setup contains stubs used only by a subset of tests (e.g. the user service
 * stub is only needed for {@code createJob} tests).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Job Service — createJob and cancelJob")
class JobServiceTest {

    // ── Mocked dependencies ───────────────────────────────────────────────────

    @Mock private Firestore         firestore;
    @Mock private CollectionReference jobsCollection;
    // RETURNS_DEEP_STUBS lets set()/update() return mock ApiFutures without explicit stubs,
    // since the production code discards their WriteResult return values.
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DocumentReference jobDocRef;
    @Mock private DocumentSnapshot    jobDocSnap;
    @Mock private QuerySnapshot       querySnapshot;
    @Mock private Query               filterQuery;   // after whereEqualTo
    @Mock private Query               statusQuery;   // after whereIn
    @Mock private Query               limitQuery;    // after limit()

    @Mock private UserService         userService;
    @Mock private GeocodingService    geocodingService;
    @Mock private AuditLogService     auditLogService;
    @Mock private Scheduler           quartzScheduler;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private JobService jobService;

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String REQUESTER_ID = "req-uid-1";
    private static final String WORKER_ID    = "wkr-uid-1";
    private static final String JOB_ID       = "job-js-test-001";
    private static final String ADDRESS_TEXT = "123 Snowy Lane, Toronto, ON M5V 1A1";

    /**
     * The authenticated caller for all Requester-initiated actions.
     * Roles are lowercase strings per the Firebase custom claims convention.
     */
    private final AuthenticatedUser requesterCaller =
        new AuthenticatedUser(REQUESTER_ID, "req@test.com", List.of("requester"));

    // ── Common setup ──────────────────────────────────────────────────────────

    /**
     * Sets up the full Firestore mock chain required by both createJob() and cancelJob().
     *
     * <p>The chain covers:
     * <ul>
     *   <li>collection("jobs") → document(id) → get/set/update</li>
     *   <li>The guardNoActiveJob() query chain: whereEqualTo → whereIn → limit → get</li>
     *   <li>userService.getUser() for the createJob requester lookup</li>
     * </ul>
     */
    @BeforeEach
    void setUp() throws Exception {

        // Base Firestore chain: collection("jobs").document(anyId)
        when(firestore.collection("jobs")).thenReturn(jobsCollection);
        when(jobsCollection.document(anyString())).thenReturn(jobDocRef);

        // getJob() explicit stub overrides the RETURNS_DEEP_STUBS default:
        // docRef.get().get() must return our controlled DocumentSnapshot.
        when(jobDocRef.get()).thenReturn(ApiFutures.immediateFuture(jobDocSnap));
        when(jobDocSnap.exists()).thenReturn(true);

        // writeJob() (docRef.set) and transitionStatus() (docRef.update) are handled
        // by RETURNS_DEEP_STUBS on jobDocRef — they return mock ApiFutures whose .get()
        // returns null, which is fine since the production code discards WriteResult.

        // guardNoActiveJob() query chain:
        //   collection("jobs")
        //     .whereEqualTo("requesterId", id)  → filterQuery
        //     .whereIn("status", activeStatuses) → statusQuery
        //     .limit(1)                           → limitQuery
        //     .get().get()                        → querySnapshot
        when(jobsCollection.whereEqualTo(anyString(), any())).thenReturn(filterQuery);
        when(filterQuery.whereIn(anyString(), any())).thenReturn(statusQuery);
        when(statusQuery.limit(anyInt())).thenReturn(limitQuery);
        when(limitQuery.get()).thenReturn(ApiFutures.immediateFuture(querySnapshot));

        // Default: no active job exists — guard passes
        when(querySnapshot.isEmpty()).thenReturn(true);

        // userService.getUser() returns a minimal User for the requester
        User requesterProfile = new User();
        requesterProfile.setUid(REQUESTER_ID);
        requesterProfile.setEmail("req@test.com");
        when(userService.getUser(REQUESTER_ID)).thenReturn(requesterProfile);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // createJob()
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createJob: happy path — job document is created in REQUESTED state")
    void createJob_success_returnsJobWithRequestedStatus() {
        when(geocodingService.geocode(ADDRESS_TEXT))
            .thenReturn(new GeocodingService.GeocodeResult(
                new GeoPoint(43.65, -79.38), "google_maps"));

        CreateJobRequest req = buildRequest(List.of("driveway"), ADDRESS_TEXT);

        Job result = jobService.createJob(requesterCaller, req);

        assertThat(result.getStatus()).isEqualTo("REQUESTED");
        assertThat(result.getRequesterId()).isEqualTo(REQUESTER_ID);
        assertThat(result.getScope()).containsExactly("driveway");
        assertThat(result.getJobId()).isNotBlank();
        // Pricing fields are null at creation — locked when Worker accepts
        assertThat(result.getTierPriceCAD()).isNull();
        assertThat(result.getTotalAmountCAD()).isNull();
    }

    @Test
    @DisplayName("createJob: multi-item scope is stored correctly")
    void createJob_multiScope_success() {
        when(geocodingService.geocode(ADDRESS_TEXT))
            .thenReturn(new GeocodingService.GeocodeResult(
                new GeoPoint(43.65, -79.38), "google_maps"));

        CreateJobRequest req = buildRequest(List.of("driveway", "sidewalk"), ADDRESS_TEXT);

        Job result = jobService.createJob(requesterCaller, req);

        assertThat(result.getScope()).containsExactlyInAnyOrder("driveway", "sidewalk");
    }

    @Test
    @DisplayName("createJob: requester already has an active job → HTTP 409 Conflict")
    void createJob_requesterHasActiveJob_throwsConflict() {
        // Override: active job exists
        when(querySnapshot.isEmpty()).thenReturn(false);

        CreateJobRequest req = buildRequest(List.of("driveway"), ADDRESS_TEXT);

        assertThatThrownBy(() -> jobService.createJob(requesterCaller, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("createJob: geocoding fails → HTTP 422 Unprocessable Entity")
    void createJob_geocodingFails_throwsUnprocessableEntity() {
        when(geocodingService.geocode(ADDRESS_TEXT))
            .thenThrow(new GeocodingService.GeocodingException(ADDRESS_TEXT));

        CreateJobRequest req = buildRequest(List.of("driveway"), ADDRESS_TEXT);

        assertThatThrownBy(() -> jobService.createJob(requesterCaller, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @DisplayName("createJob: invalid scope value → HTTP 422 Unprocessable Entity")
    void createJob_invalidScopeValue_throwsUnprocessableEntity() {
        // "roof" is not in the allowed scope set {driveway, sidewalk, both}
        CreateJobRequest req = buildRequest(List.of("roof"), ADDRESS_TEXT);

        assertThatThrownBy(() -> jobService.createJob(requesterCaller, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex ->
                assertThat(((ResponseStatusException) ex).getStatusCode())
                    .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // cancelJob()
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("cancelJob: from REQUESTED — no cancellation fee; returns previous status")
    void cancelJob_fromRequested_noFee_returnsPreviousStatus() {
        when(jobDocSnap.toObject(Job.class)).thenReturn(makeJob("REQUESTED", REQUESTER_ID, null));

        String previousStatus = jobService.cancelJob(JOB_ID, REQUESTER_ID, false);

        assertThat(previousStatus).isEqualTo("REQUESTED");
        // Verify notification sent without a cancellation fee
        verify(notificationService).sendCancellationEmail(
            eq(REQUESTER_ID), isNull(), eq(false), eq(0.0), eq(JOB_ID));
    }

    @Test
    @DisplayName("cancelJob: from PENDING_DEPOSIT — no fee; returns PENDING_DEPOSIT")
    void cancelJob_fromPendingDeposit_noFee_returnsPreviousStatus() {
        when(jobDocSnap.toObject(Job.class))
            .thenReturn(makeJob("PENDING_DEPOSIT", REQUESTER_ID, WORKER_ID));

        String previousStatus = jobService.cancelJob(JOB_ID, REQUESTER_ID, false);

        assertThat(previousStatus).isEqualTo("PENDING_DEPOSIT");
        // No fee for cancellation before CONFIRMED
        verify(notificationService).sendCancellationEmail(
            eq(REQUESTER_ID), eq(WORKER_ID), eq(false), eq(0.0), eq(JOB_ID));
    }

    @Test
    @DisplayName("cancelJob: from CONFIRMED — $10 + 13% HST cancellation fee is charged")
    void cancelJob_fromConfirmed_feeCharged_notificationIncludesFee() {
        when(jobDocSnap.toObject(Job.class))
            .thenReturn(makeJob("CONFIRMED", REQUESTER_ID, WORKER_ID));

        String previousStatus = jobService.cancelJob(JOB_ID, REQUESTER_ID, false);

        assertThat(previousStatus).isEqualTo("CONFIRMED");
        // Spec §7.3: cancellation fee is $10 + 13% HST = $11.30
        verify(notificationService).sendCancellationEmail(
            eq(REQUESTER_ID), eq(WORKER_ID), eq(true), eq(11.30), eq(JOB_ID));
    }

    @Test
    @DisplayName("cancelJob: from IN_PROGRESS — throws InvalidTransitionException")
    void cancelJob_fromInProgress_throwsInvalidTransition() {
        when(jobDocSnap.toObject(Job.class))
            .thenReturn(makeJob("IN_PROGRESS", REQUESTER_ID, WORKER_ID));

        assertThatThrownBy(() -> jobService.cancelJob(JOB_ID, REQUESTER_ID, false))
            .isInstanceOf(InvalidTransitionException.class)
            .hasMessageContaining("IN_PROGRESS");
    }

    @Test
    @DisplayName("cancelJob: from COMPLETE — throws InvalidTransitionException")
    void cancelJob_fromComplete_throwsInvalidTransition() {
        when(jobDocSnap.toObject(Job.class))
            .thenReturn(makeJob("COMPLETE", REQUESTER_ID, WORKER_ID));

        assertThatThrownBy(() -> jobService.cancelJob(JOB_ID, REQUESTER_ID, false))
            .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    @DisplayName("cancelJob: by a user who is not the requester and not an admin — AccessDeniedException")
    void cancelJob_byNonRequesterNonAdmin_throwsAccessDenied() {
        when(jobDocSnap.toObject(Job.class))
            .thenReturn(makeJob("REQUESTED", REQUESTER_ID, null));

        // "other-user" is neither the requester nor an admin
        assertThatThrownBy(() -> jobService.cancelJob(JOB_ID, "other-user-uid", false))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("cancelJob: admin can cancel any cancellable job regardless of requester")
    void cancelJob_byAdmin_succeeds() {
        when(jobDocSnap.toObject(Job.class))
            .thenReturn(makeJob("REQUESTED", REQUESTER_ID, null));

        // Admin actor cancels — different UID from requester, but isAdmin=true
        String previousStatus = jobService.cancelJob(JOB_ID, "admin-uid", true);

        assertThat(previousStatus).isEqualTo("REQUESTED");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal {@link CreateJobRequest} with the given scope and address.
     */
    private static CreateJobRequest buildRequest(List<String> scope, String addressText) {
        CreateJobRequest req = new CreateJobRequest();
        req.setScope(scope);
        req.setPropertyAddressText(addressText);
        return req;
    }

    /**
     * Builds a minimal {@link Job} with the given lifecycle state, requester, and worker.
     * Sets a dummy property address so cancellation notifications can read it.
     */
    private static Job makeJob(String status, String requesterId, String workerId) {
        Job job = new Job();
        job.setJobId(JOB_ID);
        job.setStatus(status);
        job.setRequesterId(requesterId);
        job.setWorkerId(workerId);
        Address addr = new Address(ADDRESS_TEXT);
        addr.setFullText(ADDRESS_TEXT);
        job.setPropertyAddress(addr);
        return job;
    }
}
