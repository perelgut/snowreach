package com.yosnowmow;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Transaction;
import com.yosnowmow.exception.InvalidTransitionException;
import com.yosnowmow.model.Job;
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
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the job state machine in {@link JobService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Valid state transitions succeed and return the updated job</li>
 *   <li>Transitions not in the TRANSITIONS table throw {@link InvalidTransitionException}</li>
 *   <li>Actor permission enforcement (worker-only, requester-only, admin-only)</li>
 *   <li>Dispute window expiry (configurable hours after PENDING_APPROVAL)</li>
 *   <li>Terminal states (CANCELLED, SETTLED) cannot be transitioned</li>
 * </ul>
 *
 * <p>Firestore is fully mocked; no emulator is required.  The
 * {@code runTransaction()} call is instrumented via Mockito to execute the
 * transaction lambda synchronously against a mock {@link Transaction}, so the
 * business logic inside the lambda can be exercised end-to-end.
 *
 * <p>Uses {@code LENIENT} strictness because the shared {@code @BeforeEach}
 * setup stubs methods that are not needed by every test (e.g. Quartz scheduler
 * for non-PENDING_APPROVAL transitions).
 *
 * <p>Status names use the v1.1 negotiated-marketplace vocabulary:
 * POSTED → NEGOTIATING → AGREED → ESCROW_HELD → IN_PROGRESS → PENDING_APPROVAL → RELEASED.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Job State Machine")
class StateMachineTest {

    // ── Mocked dependencies ───────────────────────────────────────────────────

    @Mock private Firestore         firestore;
    @Mock private CollectionReference jobsCollection;
    @Mock private DocumentReference   jobDocRef;
    @Mock private DocumentSnapshot    jobDocSnap;
    @Mock private Transaction         tx;

    // Secondary dependencies not exercised by transition() directly
    @Mock private UserService         userService;
    @Mock private GeocodingService    geocodingService;
    @Mock private AuditLogService     auditLogService;
    @Mock private Scheduler           quartzScheduler;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private JobService jobService;

    // ── Test constants ────────────────────────────────────────────────────────

    private static final String JOB_ID       = "job-sm-test-001";
    private static final String REQUESTER_ID = "req-uid-1";
    private static final String WORKER_ID    = "wkr-uid-1";

    // ── Common setup ──────────────────────────────────────────────────────────

    /**
     * Wires up the Firestore mock chain and instruments {@code runTransaction()}
     * to execute the transaction lambda synchronously using the mock Transaction.
     * Also stubs the Quartz scheduler for tests that transition to PENDING_APPROVAL.
     */
    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() throws Exception {

        // collection("jobs").document(id) chain — used by both getJob() and transition()
        when(firestore.collection("jobs")).thenReturn(jobsCollection);
        when(jobsCollection.document(anyString())).thenReturn(jobDocRef);

        // getJob() calls docRef.get().get() to read the document
        when(jobDocRef.get()).thenReturn(ApiFutures.immediateFuture(jobDocSnap));
        when(jobDocSnap.exists()).thenReturn(true);

        // Document updates — cast through raw ApiFuture to avoid importing WriteResult
        when(jobDocRef.update(any(Map.class))).thenReturn((ApiFuture) ApiFutures.immediateFuture(null));

        // Instrument runTransaction() to run the lambda synchronously.
        // Success → immediateFuture(result); exception → immediateFailedFuture(e).
        // The immediateFailedFuture wraps e as the cause of an ExecutionException when
        // .get() is called, and transition()'s catch block unwraps business exceptions.
        doAnswer(invocation -> {
            Transaction.Function<Object> fn = invocation.getArgument(0);
            try {
                Object result = fn.updateCallback(tx);
                return ApiFutures.immediateFuture(result);
            } catch (Exception e) {
                return ApiFutures.immediateFailedFuture(e);
            }
        }).when(firestore).runTransaction(any());

        // Inside the transaction body, tx.get(docRef) reads the job snapshot
        when(tx.get(jobDocRef)).thenReturn(ApiFutures.immediateFuture(jobDocSnap));

        // Quartz — called for PENDING_APPROVAL transitions (auto-release timer)
        when(quartzScheduler.scheduleJob(any(), any())).thenReturn(new java.util.Date());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Stubs the Firestore snapshot to return a {@link Job} in {@code status},
     * with the standard requester/worker UIDs and the test job ID.
     */
    private Job jobInState(String status) {
        Job job = new Job();
        job.setJobId(JOB_ID);
        job.setStatus(status);
        job.setRequesterId(REQUESTER_ID);
        job.setWorkerId(WORKER_ID);
        when(jobDocSnap.toObject(Job.class)).thenReturn(job);
        return job;
    }

    /**
     * Stubs the snapshot to return a PENDING_APPROVAL job whose {@code pendingApprovalAt}
     * timestamp is {@code secondsAgo} seconds before now.
     * Sets approvalWindowHours = 2 to match the spec default.
     */
    private Job pendingApprovalJobWithAge(long secondsAgo) {
        Job job = jobInState("PENDING_APPROVAL");
        long pendingApprovalSecs = Timestamp.now().getSeconds() - secondsAgo;
        job.setPendingApprovalAt(Timestamp.ofTimeSecondsAndNanos(pendingApprovalSecs, 0));
        job.setApprovalWindowHours(2);
        return job;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Valid transitions
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Stripe actor: AGREED → ESCROW_HELD succeeds (automated payment confirmation)")
    void transition_stripeActor_agreedToEscrowHeld_succeeds() {
        jobInState("AGREED");

        Job result = jobService.transition(JOB_ID, "ESCROW_HELD", "stripe", false);

        assertThat(result.getStatus()).isEqualTo("ESCROW_HELD");
    }

    @Test
    @DisplayName("Assigned worker: ESCROW_HELD → IN_PROGRESS succeeds")
    void transition_assignedWorker_escrowHeldToInProgress_succeeds() {
        jobInState("ESCROW_HELD");   // workerId = WORKER_ID

        Job result = jobService.transition(JOB_ID, "IN_PROGRESS", WORKER_ID, false);

        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("Assigned worker: IN_PROGRESS → PENDING_APPROVAL succeeds")
    void transition_assignedWorker_inProgressToPendingApproval_succeeds() {
        jobInState("IN_PROGRESS"); // workerId = WORKER_ID

        Job result = jobService.transition(JOB_ID, "PENDING_APPROVAL", WORKER_ID, false);

        assertThat(result.getStatus()).isEqualTo("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("Requester: PENDING_APPROVAL → DISPUTED within approval window succeeds")
    void transition_requester_pendingApprovalToDisputed_withinWindow_succeeds() {
        // Job submitted 30 minutes ago — well within the 2-hour approval window
        pendingApprovalJobWithAge(30 * 60);

        Job result = jobService.transition(JOB_ID, "DISPUTED", REQUESTER_ID, false);

        assertThat(result.getStatus()).isEqualTo("DISPUTED");
    }

    @Test
    @DisplayName("Admin: DISPUTED → RELEASED succeeds")
    void transition_admin_disputedToReleased_succeeds() {
        jobInState("DISPUTED");

        Job result = jobService.transition(JOB_ID, "RELEASED", "admin-uid", true);

        assertThat(result.getStatus()).isEqualTo("RELEASED");
    }

    @Test
    @DisplayName("Admin: DISPUTED → REFUNDED succeeds")
    void transition_admin_disputedToRefunded_succeeds() {
        jobInState("DISPUTED");

        Job result = jobService.transition(JOB_ID, "REFUNDED", "admin-uid", true);

        assertThat(result.getStatus()).isEqualTo("REFUNDED");
    }

    @Test
    @DisplayName("Admin: RELEASED → SETTLED succeeds")
    void transition_admin_releasedToSettled_succeeds() {
        jobInState("RELEASED");

        Job result = jobService.transition(JOB_ID, "SETTLED", "admin-uid", true);

        assertThat(result.getStatus()).isEqualTo("SETTLED");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Invalid transitions — not in the transition table
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POSTED → PENDING_APPROVAL is not in the transition table")
    void transition_postedToPendingApproval_throwsInvalidTransition() {
        jobInState("POSTED");

        assertThatThrownBy(() -> jobService.transition(JOB_ID, "PENDING_APPROVAL", "system", false))
            .isInstanceOf(InvalidTransitionException.class)
            .hasMessageContaining("POSTED")
            .hasMessageContaining("PENDING_APPROVAL");
    }

    @Test
    @DisplayName("CANCELLED is a terminal state — no further transitions allowed")
    void transition_cancelledJob_throwsInvalidTransition() {
        jobInState("CANCELLED");

        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "IN_PROGRESS", WORKER_ID, false))
            .isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    @DisplayName("SETTLED is a terminal state — no further transitions allowed")
    void transition_settledJob_throwsInvalidTransition() {
        jobInState("SETTLED");

        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "RELEASED", "admin-uid", true))
            .isInstanceOf(InvalidTransitionException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Permission enforcement
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("A different worker cannot check in to a job assigned to another worker")
    void transition_wrongWorker_escrowHeldToInProgress_throwsAccessDenied() {
        jobInState("ESCROW_HELD");  // workerId = WORKER_ID

        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "IN_PROGRESS", "different-worker-uid", false))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Requester cannot check in to a job (worker-only transition)")
    void transition_requester_escrowHeldToInProgress_throwsAccessDenied() {
        jobInState("ESCROW_HELD");  // workerId = WORKER_ID, requesterId = REQUESTER_ID

        // Requester tries to start the job — only the assigned Worker may do this
        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "IN_PROGRESS", REQUESTER_ID, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Worker cannot initiate a dispute — only the Requester may")
    void transition_worker_pendingApprovalToDisputed_throwsAccessDenied() {
        pendingApprovalJobWithAge(30 * 60);  // within window

        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "DISPUTED", WORKER_ID, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Non-admin cannot resolve a dispute (DISPUTED → RELEASED)")
    void transition_nonAdmin_disputedToReleased_throwsAccessDenied() {
        jobInState("DISPUTED");

        // Requester tries to release — only Admin may trigger this
        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "RELEASED", REQUESTER_ID, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Non-admin cannot refund a dispute (DISPUTED → REFUNDED)")
    void transition_nonAdmin_disputedToRefunded_throwsAccessDenied() {
        jobInState("DISPUTED");

        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "REFUNDED", WORKER_ID, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Dispute window enforcement
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PENDING_APPROVAL → DISPUTED fails after the approval window expires")
    void transition_requester_pendingApprovalToDisputed_windowExpired_throwsInvalidTransition() {
        // Job submitted 3 hours ago — the 2-hour approval window has expired
        pendingApprovalJobWithAge(3 * 3600);

        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "DISPUTED", REQUESTER_ID, false))
            .isInstanceOf(InvalidTransitionException.class)
            .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("PENDING_APPROVAL → DISPUTED right at the boundary (2 hours + 1 sec) is rejected")
    void transition_requester_pendingApprovalToDisputed_atBoundary_throwsInvalidTransition() {
        // 2 hours + 1 second after submission — just past the window
        pendingApprovalJobWithAge(2 * 3600 + 1);

        assertThatThrownBy(() ->
            jobService.transition(JOB_ID, "DISPUTED", REQUESTER_ID, false))
            .isInstanceOf(InvalidTransitionException.class);
    }
}
