package com.yosnowmow.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.NotificationService;
import com.yosnowmow.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Handles incoming Stripe webhook events.
 *
 * Base path: {@code /webhooks} (permitted without authentication in SecurityConfig)
 *
 * <h3>Security</h3>
 * Every request is verified using the {@code Stripe-Signature} header and the
 * webhook endpoint secret.  Requests that fail verification are rejected with 400.
 * We never trust the payload without verifying the signature.
 *
 * <h3>Idempotency</h3>
 * Before processing any event, we attempt to write a {@code stripeEvents/{eventId}}
 * document to Firestore.  If the document already exists the event has already been
 * processed and we return 200 immediately.
 *
 * <h3>Response behaviour</h3>
 * Stripe retries events until it receives a 2xx response.  We return 200 for all
 * events — even unhandled ones — so Stripe does not retry events we deliberately ignore.
 * We return 400 only for signature verification failures.
 *
 * <h3>Events handled</h3>
 * <ul>
 *   <li>{@code payment_intent.amount_capturable_updated} — funds authorized; capture
 *       the payment and store the deposit timestamp on the job.</li>
 *   <li>{@code payment_intent.succeeded} — capture completed; transition job to
 *       CONFIRMED (idempotent if already CONFIRMED).</li>
 *   <li>{@code payment_intent.payment_failed} — notify Requester (stub, wired P1-17).
 *       Job is NOT cancelled — let the Requester retry.</li>
 * </ul>
 */
@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private static final String STRIPE_EVENTS_COLLECTION = "stripeEvents";

    private final PaymentService paymentService;
    private final JobService jobService;
    private final NotificationService notificationService;
    private final Firestore firestore;

    public WebhookController(PaymentService paymentService,
                              JobService jobService,
                              NotificationService notificationService,
                              Firestore firestore) {
        this.paymentService = paymentService;
        this.jobService = jobService;
        this.notificationService = notificationService;
        this.firestore = firestore;
    }

    /**
     * Stripe webhook endpoint.
     *
     * The raw request body is injected as {@code byte[]} (not parsed JSON) so that
     * the exact byte sequence is available for HMAC-SHA256 signature verification.
     *
     * @param payload   raw HTTP request body
     * @param sigHeader value of the {@code Stripe-Signature} header
     * @return 200 OK on success or for ignored events; 400 on signature failure
     */
    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeEvent(
            @RequestBody byte[] payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        // 1. Verify Stripe signature.
        String payloadStr = new String(payload, StandardCharsets.UTF_8);
        Event event;
        try {
            event = Webhook.constructEvent(payloadStr, sigHeader, paymentService.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        log.debug("Stripe event received: {} ({})", event.getType(), event.getId());

        // 2. Idempotency check — skip already-processed events.
        try {
            if (isAlreadyProcessed(event.getId())) {
                log.debug("Stripe event {} already processed — skipping", event.getId());
                return ResponseEntity.ok().build();
            }
        } catch (Exception e) {
            log.error("Failed idempotency check for event {}: {}", event.getId(), e.getMessage(), e);
            // Continue processing — worst case we process twice but that's recoverable.
        }

        // 3. Dispatch to the correct handler.
        try {
            switch (event.getType()) {
                case "payment_intent.amount_capturable_updated" -> handleAmountCapturable(event);
                case "payment_intent.succeeded"                 -> handlePaymentSucceeded(event);
                case "payment_intent.payment_failed"            -> handlePaymentFailed(event);
                default -> log.debug("Unhandled Stripe event type: {}", event.getType());
            }
        } catch (Exception e) {
            // Log but return 200 — Stripe retries on non-200, and we don't want infinite retries
            // for bugs in our own handler.  Alert monitoring instead.
            log.error("Error processing Stripe event {} ({}): {}",
                    event.getType(), event.getId(), e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    /**
     * Fires when the customer has authorized the payment and funds are ready to capture.
     * Captures the PaymentIntent and records the deposit timestamp on the job.
     */
    private void handleAmountCapturable(Event event) {
        Optional<PaymentIntent> piOpt = extractPaymentIntent(event);
        if (piOpt.isEmpty()) return;

        PaymentIntent pi = piOpt.get();
        String jobId = pi.getMetadata().get("jobId");
        if (jobId == null) {
            log.warn("payment_intent.amount_capturable_updated: no jobId in metadata for {}",
                    pi.getId());
            return;
        }

        log.info("Capturing payment for job {} (intent {})", jobId, pi.getId());
        paymentService.capturePayment(pi.getId());

        // Record the deposit timestamp on the job (status stays PENDING_DEPOSIT here —
        // it will transition to CONFIRMED when payment_intent.succeeded fires next).
        try {
            firestore.collection("jobs").document(jobId).update(
                    "depositReceivedAt", Timestamp.now(),
                    "updatedAt",         Timestamp.now()
            ).get();
        } catch (Exception e) {
            log.error("Failed to update depositReceivedAt for job {}: {}", jobId, e.getMessage());
        }
    }

    /**
     * Fires when the PaymentIntent capture has completed (funds in our Stripe balance).
     * Transitions the job to CONFIRMED.
     */
    private void handlePaymentSucceeded(Event event) {
        Optional<PaymentIntent> piOpt = extractPaymentIntent(event);
        if (piOpt.isEmpty()) return;

        PaymentIntent pi = piOpt.get();
        String jobId = pi.getMetadata().get("jobId");
        if (jobId == null) {
            log.warn("payment_intent.succeeded: no jobId in metadata for {}", pi.getId());
            return;
        }

        try {
            // Guard: only transition if still in PENDING_DEPOSIT (idempotent).
            var job = jobService.getJob(jobId);
            if (!"PENDING_DEPOSIT".equals(job.getStatus())) {
                log.info("Job {} is already {} — skipping CONFIRMED transition", jobId,
                        job.getStatus());
                return;
            }

            Map<String, Object> extras = new HashMap<>();
            extras.put("escrowDepositedAt", Timestamp.now());
            extras.put("confirmedAt",       Timestamp.now());
            jobService.transitionStatus(jobId, "CONFIRMED", "stripe", extras);

            log.info("Job {} → CONFIRMED (intent {})", jobId, pi.getId());

            // Send confirmation emails to both parties now that payment is cleared.
            var confirmedJob = jobService.getJob(jobId);
            notificationService.sendJobConfirmedEmail(
                    confirmedJob.getRequesterId(), confirmedJob.getWorkerId(), confirmedJob);

        } catch (Exception e) {
            log.error("Failed to confirm job {}: {}", jobId, e.getMessage(), e);
            throw e; // re-throw so caller logs it as an event processing error
        }
    }

    /**
     * Fires when a payment attempt fails.
     * Notifies the Requester so they can retry; job is NOT cancelled.
     */
    private void handlePaymentFailed(Event event) {
        Optional<PaymentIntent> piOpt = extractPaymentIntent(event);
        if (piOpt.isEmpty()) return;

        PaymentIntent pi = piOpt.get();
        String jobId      = pi.getMetadata().get("jobId");
        String requesterId = pi.getMetadata().get("requesterId");

        log.warn("Payment failed for job {} (intent {}): {}",
                jobId, pi.getId(), pi.getLastPaymentError() != null
                        ? pi.getLastPaymentError().getMessage() : "unknown");

        // Notify the Requester to try again (stub — wired in P1-17).
        if (requesterId != null && jobId != null) {
            notificationService.notifyPaymentFailed(requesterId, jobId);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Writes the event ID to {@code stripeEvents/{eventId}} and returns true if the
     * document already existed (meaning we have already processed this event).
     *
     * Uses a Firestore transaction to make the check + write atomic.
     */
    private boolean isAlreadyProcessed(String eventId)
            throws InterruptedException, ExecutionException {

        DocumentReference ref = firestore.collection(STRIPE_EVENTS_COLLECTION).document(eventId);

        return Boolean.TRUE.equals(firestore.runTransaction(tx -> {
            DocumentSnapshot snap = tx.get(ref).get();
            if (snap.exists()) {
                return true; // already processed
            }
            Map<String, Object> doc = new HashMap<>();
            doc.put("processedAt", Timestamp.now());
            tx.set(ref, doc);
            return false;
        }).get());
    }

    /** Extracts and deserializes a {@link PaymentIntent} from a Stripe event. */
    private Optional<PaymentIntent> extractPaymentIntent(Event event) {
        EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
        if (!deserializer.getObject().isPresent()) {
            log.warn("Could not deserialize Stripe event {} of type {}", event.getId(),
                    event.getType());
            return Optional.empty();
        }
        StripeObject obj = deserializer.getObject().get();
        if (!(obj instanceof PaymentIntent pi)) {
            log.warn("Stripe event {} did not contain a PaymentIntent", event.getId());
            return Optional.empty();
        }
        return Optional.of(pi);
    }
}
