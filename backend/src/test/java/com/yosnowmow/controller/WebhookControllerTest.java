package com.yosnowmow.controller;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import com.yosnowmow.model.Address;
import com.yosnowmow.model.Job;
import com.yosnowmow.config.SecurityConfig;
import com.yosnowmow.service.BackgroundCheckService;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.NotificationService;
import com.yosnowmow.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link WebhookController}.
 *
 * <h3>What is tested</h3>
 * <ul>
 *   <li>Stripe signature verification — invalid signature → 400; valid → 200</li>
 *   <li>All three handled event types dispatch to the correct downstream service</li>
 *   <li>Guard conditions inside each handler (missing metadata, wrong status)</li>
 *   <li>Idempotency — duplicate events are short-circuited before any processing</li>
 *   <li>The endpoint is reachable without a Firebase token (permitAll in SecurityConfig)</li>
 * </ul>
 *
 * <h3>Why MockedStatic for Webhook.constructEvent</h3>
 * {@code Webhook.constructEvent} performs HMAC-SHA256 signature verification as a
 * static method.  We mock it with {@link MockedStatic} so we can control pass/fail
 * without computing real signatures in tests.
 *
 * <h3>Spring context notes</h3>
 * {@code FirebaseConfig} is not loaded by the {@code @WebMvcTest} slice (plain
 * {@code @Configuration} beans that provide no web-layer beans are excluded).
 * {@code @MockBean FirebaseAuth} satisfies the {@link com.yosnowmow.security.FirebaseTokenFilter}
 * constructor.  The real filter runs but calls {@code shouldNotFilter()} → {@code true}
 * for all {@code /webhooks/**} paths and therefore skips token verification entirely,
 * matching production behaviour.
 */
@WebMvcTest(controllers = WebhookController.class)
@Import(SecurityConfig.class)   // @WebMvcTest excludes @EnableWebSecurity configs by default
@ActiveProfiles("test")
@SuppressWarnings("unchecked")   // Mockito generic-return-type stubs (runTransaction)
@DisplayName("WebhookController — Stripe event handling")
class WebhookControllerTest {

    private static final String STRIPE_ENDPOINT = "/webhooks/stripe";

    /** Payload content is irrelevant — Webhook.constructEvent is mocked. */
    private static final byte[] DUMMY_PAYLOAD = "{}".getBytes();

    private static final String VALID_SIG   = "t=1234,v1=valid";
    private static final String INVALID_SIG = "t=1234,v1=bad";

    private static final String JOB_ID       = "job-wh-test-001";
    private static final String PI_ID        = "pi_test_123";
    private static final String EVT_ID       = "evt_test_123";
    private static final String REQUESTER_ID = "req-wh-1";
    private static final String WORKER_ID    = "wkr-wh-1";

    @Autowired
    private MockMvc mockMvc;

    // ── Spring beans required by WebhookController ───────────────────────────

    @MockBean private PaymentService         paymentService;
    @MockBean private JobService             jobService;
    @MockBean private NotificationService    notificationService;
    @MockBean private Firestore              firestore;
    @MockBean private BackgroundCheckService backgroundCheckService;

    // FirebaseAuth satisfies FirebaseTokenFilter's constructor.
    // The filter is a Filter bean and is included in the @WebMvcTest slice.
    @MockBean private FirebaseAuth        firebaseAuth;

    // ── Firestore sub-mocks (plain Mockito mocks — not Spring beans) ─────────

    private CollectionReference stripeEventsCol;
    private DocumentReference   stripeEventDoc;
    private CollectionReference jobsCol;
    private DocumentReference   jobDoc;

    @BeforeEach
    void setUp() {
        // ── stripeEvents collection (isAlreadyProcessed) ──────────────────────
        stripeEventsCol = mock(CollectionReference.class);
        stripeEventDoc  = mock(DocumentReference.class);
        when(firestore.collection("stripeEvents")).thenReturn(stripeEventsCol);
        when(stripeEventsCol.document(anyString())).thenReturn(stripeEventDoc);

        // Default: event NOT yet processed → processing continues
        doReturn(ApiFutures.immediateFuture(Boolean.FALSE))
            .when(firestore).runTransaction(any());

        // ── jobs collection (handleAmountCapturable update) ───────────────────
        jobsCol = mock(CollectionReference.class);
        jobDoc  = mock(DocumentReference.class, Answers.RETURNS_DEEP_STUBS);
        when(firestore.collection("jobs")).thenReturn(jobsCol);
        when(jobsCol.document(anyString())).thenReturn(jobDoc);

        // PaymentService must expose a non-null webhook secret for constructEvent
        when(paymentService.getWebhookSecret()).thenReturn("whsec_test");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Signature verification
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("invalid Stripe signature → 400 Bad Request")
    void invalidSignature_returns400() throws Exception {
        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(INVALID_SIG), anyString()))
                  .thenThrow(new SignatureVerificationException("bad sig", INVALID_SIG));

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", INVALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("webhook endpoint is reachable without Authorization header (permitAll)")
    void webhookEndpoint_noAuthHeader_isPermitted() throws Exception {
        // No Authorization header — FirebaseTokenFilter skips /webhooks/** entirely.
        // This test documents the production security posture: Stripe's own signature
        // verification is the only authentication mechanism for this endpoint.
        Event event = buildEvent("customer.subscription.deleted", EVT_ID, null);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Unhandled event type
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("unrecognised event type → 200, no downstream service calls")
    void unknownEventType_returns200_noDownstreamCalls() throws Exception {
        Event event = buildEvent("customer.subscription.deleted", EVT_ID, null);

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }

        // Stripe retries on non-2xx — we must not reject events we choose to ignore
        verifyNoInteractions(jobService, notificationService);
        verify(paymentService, never()).capturePayment(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // payment_intent.amount_capturable_updated
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("amount_capturable_updated: jobId in metadata → 200, capturePayment called")
    void amountCapturableUpdated_withJobId_capturesCalled() throws Exception {
        Event event = buildEvent(
            "payment_intent.amount_capturable_updated", EVT_ID,
            buildPaymentIntent(PI_ID, JOB_ID, null));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }

        verify(paymentService).capturePayment(PI_ID);
    }

    @Test
    @DisplayName("amount_capturable_updated: no jobId in metadata → 200, capture skipped")
    void amountCapturableUpdated_noJobId_skipsCapture() throws Exception {
        // PaymentIntent with no "jobId" in metadata — handler exits early
        Event event = buildEvent(
            "payment_intent.amount_capturable_updated", EVT_ID,
            buildPaymentIntent(PI_ID, null, null));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }

        verify(paymentService, never()).capturePayment(any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // payment_intent.succeeded
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("payment_intent.succeeded: job in AGREED → 200, transitions to ESCROW_HELD")
    void paymentSucceeded_pendingDeposit_transitionsToConfirmed() throws Exception {
        when(jobService.getJob(JOB_ID))
            .thenReturn(makeJob(JOB_ID, "AGREED", REQUESTER_ID, WORKER_ID));

        Event event = buildEvent(
            "payment_intent.succeeded", EVT_ID,
            buildPaymentIntent(PI_ID, JOB_ID, REQUESTER_ID));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }

        verify(jobService).transitionStatus(eq(JOB_ID), eq("ESCROW_HELD"), eq("stripe"), any());
    }

    @Test
    @DisplayName("payment_intent.succeeded: job already ESCROW_HELD → 200, no duplicate transition")
    void paymentSucceeded_alreadyConfirmed_skipsTransition() throws Exception {
        // Idempotency guard: job is already past AGREED — do not re-transition
        when(jobService.getJob(JOB_ID))
            .thenReturn(makeJob(JOB_ID, "ESCROW_HELD", REQUESTER_ID, WORKER_ID));

        Event event = buildEvent(
            "payment_intent.succeeded", EVT_ID,
            buildPaymentIntent(PI_ID, JOB_ID, REQUESTER_ID));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }

        verify(jobService, never()).transitionStatus(any(), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // payment_intent.payment_failed
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("payment_intent.payment_failed → 200, requester notified")
    void paymentFailed_notifiesRequester() throws Exception {
        Event event = buildEvent(
            "payment_intent.payment_failed", EVT_ID,
            buildPaymentIntent(PI_ID, JOB_ID, REQUESTER_ID));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }

        // Job is NOT cancelled — Requester is notified to retry
        verify(notificationService).notifyPaymentFailed(REQUESTER_ID, JOB_ID);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Idempotency
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("duplicate event (already processed) → 200, no downstream processing")
    void duplicateEvent_returns200_noProcessing() throws Exception {
        // Override default: this event was already processed in a previous delivery
        doReturn(ApiFutures.immediateFuture(Boolean.TRUE))
            .when(firestore).runTransaction(any());

        Event event = buildEvent(
            "payment_intent.amount_capturable_updated", EVT_ID,
            buildPaymentIntent(PI_ID, JOB_ID, null));

        try (MockedStatic<Webhook> mocked = mockStatic(Webhook.class)) {
            mocked.when(() -> Webhook.constructEvent(anyString(), eq(VALID_SIG), anyString()))
                  .thenReturn(event);

            mockMvc.perform(post(STRIPE_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Stripe-Signature", VALID_SIG)
                        .content(DUMMY_PAYLOAD))
                   .andExpect(status().isOk());
        }

        // The idempotency guard must prevent all downstream work
        verify(paymentService, never()).capturePayment(any());
        verifyNoInteractions(jobService, notificationService);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a mocked Stripe {@link Event} with the given type, ID, and optional
     * {@link PaymentIntent}.  The deserializer is pre-wired to return the PI if provided.
     */
    private static Event buildEvent(String type, String id, PaymentIntent pi) {
        Event event = mock(Event.class);
        when(event.getType()).thenReturn(type);
        when(event.getId()).thenReturn(id);

        if (pi != null) {
            EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
            when(deserializer.getObject()).thenReturn(Optional.of(pi));
            when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        }
        return event;
    }

    /**
     * Builds a mocked Stripe {@link PaymentIntent} with the given ID and optional
     * metadata values.
     *
     * @param piId        PaymentIntent ID (e.g. {@code "pi_test_123"})
     * @param jobId       value for the {@code "jobId"} metadata key; {@code null} to omit
     * @param requesterId value for the {@code "requesterId"} metadata key; {@code null} to omit
     */
    private static PaymentIntent buildPaymentIntent(String piId, String jobId, String requesterId) {
        PaymentIntent pi = mock(PaymentIntent.class);
        when(pi.getId()).thenReturn(piId);

        Map<String, String> meta = new HashMap<>();
        if (jobId       != null) meta.put("jobId",       jobId);
        if (requesterId != null) meta.put("requesterId", requesterId);
        when(pi.getMetadata()).thenReturn(meta);

        return pi;
    }

    /**
     * Builds a minimal {@link Job} for use as a {@link JobService#getJob} return value.
     */
    private static Job makeJob(String jobId, String status, String requesterId, String workerId) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setStatus(status);
        job.setRequesterId(requesterId);
        job.setWorkerId(workerId);

        Address addr = new Address("123 Test St, Toronto, ON");
        addr.setFullText("123 Test St, Toronto, ON");
        job.setPropertyAddress(addr);
        return job;
    }
}
