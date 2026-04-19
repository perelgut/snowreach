package com.yosnowmow.config;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the custom Spring Boot Actuator health indicators
 * defined in {@link HealthConfig} (P2-08).
 *
 * <p>Each indicator bean is built directly via the {@code @Bean} factory method
 * under test — no Spring context needed.
 *
 * <p>{@code pingDocRef} uses {@code RETURNS_DEEP_STUBS} so that the two-level chain
 * {@code pingDocRef.get().get(5, SECONDS)} can be stubbed without intermediate variables.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealthConfig — Custom Actuator Indicators")
class HealthIndicatorTest {

    // ── Firestore mocks ────────────────────────────────────────────────────────

    @Mock private Firestore           firestore;
    @Mock private CollectionReference healthCollection;

    /**
     * RETURNS_DEEP_STUBS so that {@code pingDocRef.get().get(long, TimeUnit)} can
     * be stubbed directly; otherwise {@code pingDocRef.get()} returns {@code null}
     * and chaining {@code .get(5, SECONDS)} throws NullPointerException.
     */
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DocumentReference pingDocRef;

    // ── Quartz mock ────────────────────────────────────────────────────────────

    @Mock private Scheduler scheduler;

    // ── HealthConfig under test ────────────────────────────────────────────────

    private final HealthConfig config = new HealthConfig();

    private HealthIndicator firebaseIndicator() {
        return config.firebaseHealthIndicator(firestore);
    }

    private HealthIndicator quartzIndicator() {
        return config.quartzHealthIndicator(scheduler);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Firebase health indicator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Firebase: Firestore ping succeeds → UP with 'reachable' detail")
    void firebase_firestoreReachable_returnsUp() {
        stubFirestorePingChain();
        // Deep-stubs default: pingDocRef.get().get(5, SECONDS) returns a mock
        // DocumentSnapshot (no exception thrown) → indicator returns UP.

        Health health = firebaseIndicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("firestore", "reachable");
    }

    @Test
    @DisplayName("Firebase: Firestore ping times out → DOWN with timeout detail")
    void firebase_pingTimesOut_returnsDown() throws Exception {
        stubFirestorePingChain();
        when(pingDocRef.get().get(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new TimeoutException("5 s timeout"));

        Health health = firebaseIndicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("firestore", "timeout after 5 s");
    }

    @Test
    @DisplayName("Firebase: Firestore unreachable (ExecutionException) → DOWN with error detail")
    void firebase_executionException_returnsDown() throws Exception {
        stubFirestorePingChain();
        when(pingDocRef.get().get(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new ExecutionException("connection refused", new RuntimeException()));

        Health health = firebaseIndicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("firestore", "unreachable");
        assertThat(health.getDetails()).containsKey("error");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Quartz health indicator
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Quartz: scheduler started and not in standby → UP with 'running' detail")
    void quartz_schedulerRunning_returnsUp() throws SchedulerException {
        when(scheduler.isStarted()).thenReturn(true);
        when(scheduler.isInStandbyMode()).thenReturn(false);
        when(scheduler.getSchedulerName()).thenReturn("QuartzScheduler");

        Health health = quartzIndicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("status", "running");
        assertThat(health.getDetails()).containsEntry("schedulerName", "QuartzScheduler");
    }

    @Test
    @DisplayName("Quartz: scheduler not yet started → DOWN with 'not started' detail")
    void quartz_notStarted_returnsDown() throws SchedulerException {
        when(scheduler.isStarted()).thenReturn(false);

        Health health = quartzIndicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("quartz", "not started");
    }

    @Test
    @DisplayName("Quartz: scheduler in standby mode → DOWN with 'in standby mode' detail")
    void quartz_inStandby_returnsDown() throws SchedulerException {
        when(scheduler.isStarted()).thenReturn(true);
        when(scheduler.isInStandbyMode()).thenReturn(true);

        Health health = quartzIndicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("quartz", "in standby mode");
    }

    @Test
    @DisplayName("Quartz: SchedulerException thrown → DOWN with error detail")
    void quartz_schedulerException_returnsDown() throws SchedulerException {
        when(scheduler.isStarted()).thenThrow(new SchedulerException("Quartz internal error"));

        Health health = quartzIndicator().health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("quartz", "error");
        assertThat(health.getDetails()).containsKey("error");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Stubs the Firestore collection/document chain so that
     * {@code firestore.collection("_health").document("ping")} returns
     * {@code pingDocRef} (a deep-stub mock).  Tests then stub or rely on
     * deep-stub defaults for the terminal {@code .get().get(5, SECONDS)} call.
     */
    private void stubFirestorePingChain() {
        when(firestore.collection("_health")).thenReturn(healthCollection);
        when(healthCollection.document("ping")).thenReturn(pingDocRef);
    }
}
