package com.yosnowmow;

import com.google.api.core.ApiFutures; // used for immediateFuture(docSnap) and immediateFuture(querySnapshot)
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.GeoPoint;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.mockito.Answers;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import com.yosnowmow.service.MatchingService;
import com.yosnowmow.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the worker-matching algorithm in {@link MatchingService}.
 *
 * <p>Tests verify that the algorithm:
 * <ul>
 *   <li>Includes Workers whose distance ≤ serviceRadiusKm (Haversine)</li>
 *   <li>Excludes Workers whose distance > serviceRadiusKm</li>
 *   <li>Sorts results: rating DESC, distance ASC</li>
 *   <li>Excludes dispatcher Workers when {@code personalWorkerOnly} is true</li>
 *   <li>Extends the effective radius by 10% when {@code bufferOptIn} is true</li>
 *   <li>Bypasses the algorithm entirely when {@code selectedWorkerIds} is set</li>
 * </ul>
 *
 * <p>The {@code @Async} annotation on {@link MatchingService#matchAndStoreWorkers}
 * is inactive here (no Spring proxy), so the method runs synchronously, making
 * assertions straightforward.
 *
 * <p>The algorithm result is captured from the Firestore update call that stores
 * {@code matchedWorkerIds} back onto the job document.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Matching Service — Worker Matching Algorithm")
class MatchingServiceTest {

    // ── Mocked dependencies ───────────────────────────────────────────────────

    @Mock private Firestore             firestore;
    @Mock private NotificationService   notificationService;

    // Firestore: jobs collection chain.
    // RETURNS_DEEP_STUBS lets update() return a mock ApiFuture without explicit stubbing —
    // the production code discards the WriteResult return value.
    @Mock private CollectionReference jobsCollection;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DocumentReference jobDocRef;
    @Mock private DocumentSnapshot    jobDocSnap;

    // Firestore: users collection chain
    @Mock private CollectionReference usersCollection;
    @Mock private Query               usersQuery;
    @Mock private QuerySnapshot       usersQuerySnapshot;

    // Firestore: capacity check chain — jobs.whereEqualTo("workerId",uid).whereIn("status",[...]).get()
    @Mock private Query               capacityCheckQuery;
    @Mock private QuerySnapshot       capacityCheckSnap;

    @InjectMocks
    private MatchingService matchingService;

    // ── Coordinates ───────────────────────────────────────────────────────────

    /**
     * Property coordinates for all test jobs — Toronto downtown area.
     * Workers are placed at varying distances from this point.
     */
    private static final double JOB_LAT = 43.6700;
    private static final double JOB_LON = -79.3900;

    private static final String JOB_ID = "job-ms-test-001";

    /**
     * Distance reference (1° latitude ≈ 111.19 km at any latitude).
     * Used to convert km to degrees when placing test Workers.
     */
    private static final double KM_PER_LAT_DEG = 111.19;

    // ── Common setup ──────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {

        // Jobs collection chain: collection("jobs").document(JOB_ID).get()
        when(firestore.collection("jobs")).thenReturn(jobsCollection);
        when(jobsCollection.document(anyString())).thenReturn(jobDocRef);

        // Explicit stub overrides RETURNS_DEEP_STUBS for get() so we control the snapshot.
        when(jobDocRef.get()).thenReturn(ApiFutures.immediateFuture(jobDocSnap));
        when(jobDocSnap.exists()).thenReturn(true);

        // Users collection chain: collection("users").whereEqualTo("worker.status", "available").get()
        when(firestore.collection("users")).thenReturn(usersCollection);
        when(usersCollection.whereEqualTo(anyString(), any())).thenReturn(usersQuery);
        when(usersQuery.get()).thenReturn(ApiFutures.immediateFuture(usersQuerySnapshot));

        // Capacity-check chain (P2-05): collection("jobs").whereEqualTo("workerId",uid).whereIn(...).get()
        // Default: 0 active jobs → worker has capacity.  Override in specific tests to simulate full capacity.
        when(jobsCollection.whereEqualTo(anyString(), any())).thenReturn(capacityCheckQuery);
        when(capacityCheckQuery.whereIn(anyString(), any())).thenReturn(capacityCheckQuery);
        when(capacityCheckQuery.get()).thenReturn(ApiFutures.immediateFuture(capacityCheckSnap));
        when(capacityCheckSnap.size()).thenReturn(0);

        // Job update (matchedWorkerIds) is handled by RETURNS_DEEP_STUBS on jobDocRef —
        // the deep-stub mock ApiFuture.get() returns null, which is discarded by the caller.
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Radius filtering
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Worker within service radius is included in matched candidates")
    void matching_workerWithinRadius_isIncluded() {
        // Worker is ~2 km north of the job; service radius is 10 km → in range
        User worker = makeWorkerUser("wkr-in-range", latOffsetKm(2.0), JOB_LON, 10.0, 4.5);
        stubJobAndWorkers(makeJob(false), List.of(worker));

        matchingService.matchAndStoreWorkers(JOB_ID);

        assertThat(captureMatchedWorkerIds()).contains("wkr-in-range");
    }

    @Test
    @DisplayName("Worker outside service radius is excluded from matched candidates")
    void matching_workerOutsideRadius_isExcluded() {
        // Worker is ~26 km north (North York); service radius is 5 km → out of range
        User worker = makeWorkerUser("wkr-far-away", 43.90, -79.40, 5.0, 4.5);
        stubJobAndWorkers(makeJob(false), List.of(worker));

        matchingService.matchAndStoreWorkers(JOB_ID);

        assertThat(captureMatchedWorkerIds()).doesNotContain("wkr-far-away");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Sort order: rating DESC, distance ASC
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Candidates are ordered: highest rating first, then closest distance")
    void matching_sortsByRatingDescThenDistanceAsc() {
        // Both Workers are within a 5 km radius.
        // Worker-A: ~3 km away, rating 4.2  (further, lower rating → should be second)
        // Worker-B: ~2 km away, rating 4.8  (closer, higher rating  → should be first)
        User workerA = makeWorkerUser("wkr-A", latOffsetKm(3.0), JOB_LON, 5.0, 4.2);
        User workerB = makeWorkerUser("wkr-B", latOffsetKm(2.0), JOB_LON, 5.0, 4.8);
        stubJobAndWorkers(makeJob(false), List.of(workerA, workerB));

        matchingService.matchAndStoreWorkers(JOB_ID);

        List<String> matched = captureMatchedWorkerIds();
        // Higher-rated worker must come first regardless of Firestore query order
        assertThat(matched).containsExactly("wkr-B", "wkr-A");
    }

    @Test
    @DisplayName("When ratings are equal, closer worker is ranked first")
    void matching_equalRating_closerWorkerRanksFirst() {
        // Both workers have rating 4.5; Worker-C is closer → should be first
        User workerC = makeWorkerUser("wkr-C", latOffsetKm(1.5), JOB_LON, 5.0, 4.5);
        User workerD = makeWorkerUser("wkr-D", latOffsetKm(4.0), JOB_LON, 5.0, 4.5);
        stubJobAndWorkers(makeJob(false), List.of(workerD, workerC)); // intentionally reversed

        matchingService.matchAndStoreWorkers(JOB_ID);

        List<String> matched = captureMatchedWorkerIds();
        assertThat(matched).containsExactly("wkr-C", "wkr-D");
    }

    @Test
    @DisplayName("Workers without a rating (< 10 jobs) are ranked last")
    void matching_nullRating_workerRanksLast() {
        // Worker with a 4.0 rating vs Worker with null rating (< 10 completed jobs)
        User ratedWorker   = makeWorkerUser("wkr-rated",   latOffsetKm(1.0), JOB_LON, 5.0, 4.0);
        User unratedWorker = makeWorkerUser("wkr-unrated", latOffsetKm(1.0), JOB_LON, 5.0, null);
        stubJobAndWorkers(makeJob(false), List.of(unratedWorker, ratedWorker));

        matchingService.matchAndStoreWorkers(JOB_ID);

        List<String> matched = captureMatchedWorkerIds();
        // Rated worker (effective rating 4.0) must come before unrated worker (effective -1.0)
        assertThat(matched.indexOf("wkr-rated")).isLessThan(matched.indexOf("wkr-unrated"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // personalWorkerOnly flag
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("personalWorkerOnly=true excludes dispatcher Workers")
    void matching_personalWorkerOnly_excludesDispatchers() {
        User dispatcher  = makeWorkerUser("wkr-dispatcher", latOffsetKm(2.0), JOB_LON, 10.0, 4.5);
        User personalWkr = makeWorkerUser("wkr-personal",   latOffsetKm(2.0), JOB_LON, 10.0, 4.0);

        dispatcher.getWorker().setDesignation("dispatcher");
        personalWkr.getWorker().setDesignation("personal");

        // Job explicitly requires a personal (non-dispatcher) Worker
        stubJobAndWorkers(makeJob(true), List.of(dispatcher, personalWkr));

        matchingService.matchAndStoreWorkers(JOB_ID);

        List<String> matched = captureMatchedWorkerIds();
        assertThat(matched).containsOnly("wkr-personal");
        assertThat(matched).doesNotContain("wkr-dispatcher");
    }

    @Test
    @DisplayName("personalWorkerOnly=false includes both personal and dispatcher Workers")
    void matching_notPersonalOnly_includesBothTypes() {
        User dispatcher  = makeWorkerUser("wkr-dispatcher", latOffsetKm(2.0), JOB_LON, 10.0, 4.5);
        User personalWkr = makeWorkerUser("wkr-personal",   latOffsetKm(2.0), JOB_LON, 10.0, 4.0);

        dispatcher.getWorker().setDesignation("dispatcher");
        personalWkr.getWorker().setDesignation("personal");

        // Job does NOT restrict to personal Workers
        stubJobAndWorkers(makeJob(false), List.of(dispatcher, personalWkr));

        matchingService.matchAndStoreWorkers(JOB_ID);

        List<String> matched = captureMatchedWorkerIds();
        assertThat(matched).containsExactlyInAnyOrder("wkr-dispatcher", "wkr-personal");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Buffer opt-in
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("bufferOptIn=true extends effective radius by 10%")
    void matching_bufferOptIn_extendsRadius() {
        // Worker is ~5.25 km from the job; service radius is 5.0 km.
        //   Without buffer: 5.25 > 5.00 → EXCLUDED
        //   With    buffer: 5.25 < 5.50 (5.0 × 1.10) → INCLUDED
        double edgeLat = latOffsetKm(5.25);

        User workerWithBuffer    = makeWorkerUser("wkr-buffer",    edgeLat, JOB_LON, 5.0, 4.0);
        User workerWithoutBuffer = makeWorkerUser("wkr-no-buffer", edgeLat, JOB_LON, 5.0, 4.0);

        workerWithBuffer.getWorker().setBufferOptIn(true);
        workerWithoutBuffer.getWorker().setBufferOptIn(false);

        stubJobAndWorkers(makeJob(false), List.of(workerWithBuffer, workerWithoutBuffer));

        matchingService.matchAndStoreWorkers(JOB_ID);

        List<String> matched = captureMatchedWorkerIds();
        assertThat(matched).contains("wkr-buffer");
        assertThat(matched).doesNotContain("wkr-no-buffer");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // selectedWorkerIds bypass
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("selectedWorkerIds bypasses the matching algorithm entirely")
    void matching_preSelectedWorkers_bypassesAlgorithm() {
        Job job = makeJob(false);
        job.setSelectedWorkerIds(List.of("selected-wkr-1", "selected-wkr-2"));
        stubJob(job);

        // A nearby available Worker exists — but it should NOT be matched because
        // the Requester explicitly pre-selected different Workers.
        // Build the doc mock BEFORE the when().thenReturn() call to avoid Mockito's
        // "unfinished stubbing" error that occurs when when() is nested inside thenReturn().
        User randomWorker = makeWorkerUser("random-wkr", latOffsetKm(1.0), JOB_LON, 10.0, 5.0);
        QueryDocumentSnapshot randomDoc = mockWorkerDoc("random-wkr", randomWorker);
        when(usersQuerySnapshot.getDocuments()).thenReturn(List.of(randomDoc));

        matchingService.matchAndStoreWorkers(JOB_ID);

        List<String> matched = captureMatchedWorkerIds();
        // Exact order from selectedWorkerIds is preserved
        assertThat(matched).containsExactly("selected-wkr-1", "selected-wkr-2");
        assertThat(matched).doesNotContain("random-wkr");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Capacity enforcement (P2-05) — live Firestore query replaces cached field
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Worker at full capacity (1/1 active jobs) is excluded from candidates")
    void matching_workerAtCapacity_isExcluded() throws Exception {
        // Worker has capacityMax=1; live query reports 1 active job → at capacity → excluded.
        User worker = makeWorkerUser("wkr-full", latOffsetKm(2.0), JOB_LON, 10.0, 4.5);
        stubJobAndWorkers(makeJob(false), List.of(worker));

        // Simulate 1 live active job for this worker (equals capacityMax of 1).
        when(capacityCheckSnap.size()).thenReturn(1);

        matchingService.matchAndStoreWorkers(JOB_ID);

        assertThat(captureMatchedWorkerIds()).doesNotContain("wkr-full");
    }

    @Test
    @DisplayName("Worker below capacity (0/2 active jobs) is included in candidates")
    void matching_workerBelowCapacity_isIncluded() throws Exception {
        // Worker has capacityMax=2; live query reports 1 active job → still has capacity → included.
        User worker = makeWorkerUser("wkr-has-cap", latOffsetKm(2.0), JOB_LON, 10.0, 4.5);
        worker.getWorker().setCapacityMax(2);
        stubJobAndWorkers(makeJob(false), List.of(worker));

        // Simulate 1 live active job — below the max of 2.
        when(capacityCheckSnap.size()).thenReturn(1);

        matchingService.matchAndStoreWorkers(JOB_ID);

        assertThat(captureMatchedWorkerIds()).contains("wkr-has-cap");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the latitude that is {@code km} kilometres north of the job location.
     * Uses 1° ≈ {@value #KM_PER_LAT_DEG} km, valid for Ontario latitudes.
     */
    private static double latOffsetKm(double km) {
        return JOB_LAT + (km / KM_PER_LAT_DEG);
    }

    /**
     * Creates a test job at the standard property coordinates.
     *
     * @param personalWorkerOnly when true, only Workers with designation="personal" are eligible
     */
    private static Job makeJob(boolean personalWorkerOnly) {
        Job job = new Job();
        job.setJobId(JOB_ID);
        job.setStatus("POSTED");
        job.setPropertyCoords(new GeoPoint(JOB_LAT, JOB_LON));
        job.setPersonalWorkerOnly(personalWorkerOnly);
        return job;
    }

    /**
     * Creates a User with an embedded WorkerProfile located at ({@code lat}, {@code lon}).
     *
     * <p>Defaults: designation="personal", status="available", capacityMax=1,
     * activeJobCount=0, bufferOptIn=false.
     */
    private static User makeWorkerUser(String uid, double lat, double lon,
                                       double radiusKm, Double rating) {
        WorkerProfile wp = new WorkerProfile();
        wp.setStatus("available");
        wp.setBaseCoords(new GeoPoint(lat, lon));
        wp.setServiceRadiusKm(radiusKm);
        wp.setCapacityMax(1);
        wp.setActiveJobCount(0);
        wp.setDesignation("personal");
        wp.setBufferOptIn(false);
        wp.setRating(rating);

        User user = new User();
        user.setUid(uid);
        user.setWorker(wp);
        return user;
    }

    /**
     * Creates a mocked {@link QueryDocumentSnapshot} that returns {@code user}
     * when deserialized and {@code uid} when {@code getId()} is called.
     */
    @SuppressWarnings("unchecked")
    private static QueryDocumentSnapshot mockWorkerDoc(String uid, User user) {
        QueryDocumentSnapshot doc = mock(QueryDocumentSnapshot.class);
        when(doc.getId()).thenReturn(uid);
        when(doc.toObject(User.class)).thenReturn(user);
        return doc;
    }

    /**
     * Stubs the Firestore job snapshot and the users query to return the given job
     * and worker list for a {@link MatchingService#matchAndStoreWorkers} call.
     */
    private void stubJobAndWorkers(Job job, List<User> workers) {
        stubJob(job);
        List<QueryDocumentSnapshot> docs = new ArrayList<>();
        for (User w : workers) {
            docs.add(mockWorkerDoc(w.getUid(), w));
        }
        when(usersQuerySnapshot.getDocuments()).thenReturn(docs);
    }

    /** Stubs only the job snapshot; the users query mock is configured separately. */
    private void stubJob(Job job) {
        when(jobDocSnap.toObject(Job.class)).thenReturn(job);
    }

    /**
     * Captures and returns the {@code matchedWorkerIds} list that
     * {@link MatchingService#matchAndStoreWorkers} stored back on the job document.
     *
     * <p>The service calls:
     * {@code jobDocRef.update("matchedWorkerIds", candidateUids, "updatedAt", timestamp)}
     * which is the Firestore vararg update form.  The second argument (candidateUids)
     * is captured here.
     */
    @SuppressWarnings("unchecked")
    private List<String> captureMatchedWorkerIds() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        // Verify the update was called and capture the second argument (the worker ID list)
        verify(jobDocRef).update(eq("matchedWorkerIds"), captor.capture(), any(), any());
        return (List<String>) captor.getValue();
    }
}
