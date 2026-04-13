package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Stripe payment operations for YoSnowMow.
 *
 * <h3>Escrow flow (P1-11)</h3>
 * <ol>
 *   <li>Worker accepts → job reaches PENDING_DEPOSIT; {@code totalAmountCAD} is set.</li>
 *   <li>Requester calls {@code POST /api/jobs/{jobId}/payment-intent}; backend creates a
 *       Stripe PaymentIntent with {@code capture_method=MANUAL} and returns the
 *       {@code clientSecret} for Stripe.js to confirm.</li>
 *   <li>Customer confirms in Stripe.js → Stripe fires
 *       {@code payment_intent.amount_capturable_updated}; webhook calls
 *       {@link #capturePayment(String)} which charges the card and moves funds to the
 *       platform Stripe balance.</li>
 *   <li>Stripe fires {@code payment_intent.succeeded} → webhook transitions job to
 *       CONFIRMED.</li>
 * </ol>
 *
 * <p>Design note: {@code capture_method=MANUAL} is used so the backend controls exactly
 * when the customer's card is charged.  The 7-day Stripe authorization window is
 * acceptable for snow-clearing jobs (typically same-day).  Phase 2 can extend this
 * with SetupIntents if multi-day scheduling becomes common.</p>
 *
 * <h3>Worker payout (P1-12)</h3>
 * <ol>
 *   <li>Worker completes Stripe Connect Express onboarding via
 *       {@link #createConnectOnboardingLink}.</li>
 *   <li>At RELEASED state: {@link #releasePayment(String)} creates a Stripe Transfer to
 *       the worker's Connected account.  The platform retains the commission automatically
 *       (only {@code workerPayoutCAD} is transferred).</li>
 * </ol>
 *
 * <h3>Refunds</h3>
 * {@link #refundJob(String)} cancels the Stripe PaymentIntent and creates a full refund.
 * Called by admin at REFUNDED state.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final String JOBS_COLLECTION  = "jobs";
    private static final String USERS_COLLECTION = "users";

    /** Stripe currency code for Canadian dollars. */
    private static final String CURRENCY_CAD = "cad";

    /** Stripe Connect account country — all workers are in Canada. */
    private static final String CONNECT_COUNTRY = "CA";

    @Value("${yosnowmow.stripe.secret-key}")
    private String secretKey;

    @Value("${yosnowmow.stripe.webhook-secret}")
    private String webhookSecret;

    private final Firestore firestore;
    private final JobService jobService;
    private final AuditLogService auditLogService;

    public PaymentService(Firestore firestore,
                          JobService jobService,
                          AuditLogService auditLogService) {
        this.firestore = firestore;
        this.jobService = jobService;
        this.auditLogService = auditLogService;
    }

    /** Sets the Stripe API key once Spring has injected the {@code @Value} fields. */
    @PostConstruct
    public void init() {
        Stripe.apiKey = secretKey;
        log.info("Stripe initialized (key prefix: {}…)", secretKey.substring(0, 7));
    }

    // ── Escrow — P1-11 ────────────────────────────────────────────────────────

    /**
     * Creates a Stripe PaymentIntent for the escrow deposit on a job.
     *
     * The intent uses {@code capture_method=MANUAL} so the customer's card is
     * authorized but not charged until {@link #capturePayment(String)} is called
     * (which happens automatically via the Stripe webhook when authorization
     * completes).
     *
     * Idempotent: if the job already has a {@code stripePaymentIntentId} the
     * existing intent is retrieved and its client secret is returned.
     *
     * @param jobId the job to create a payment intent for
     * @return Stripe PaymentIntent client secret (for Stripe.js {@code confirmCardPayment})
     * @throws ResponseStatusException 422 if the job is not in PENDING_DEPOSIT state
     *                                     or if pricing has not been set
     */
    public String createEscrowIntent(String jobId) {
        try {
            Job job = jobService.getJob(jobId);

            if (!"PENDING_DEPOSIT".equals(job.getStatus())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Job is not awaiting deposit (status: " + job.getStatus() + ")");
            }
            if (job.getTotalAmountCAD() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Job pricing has not been calculated yet");
            }

            // Idempotency: return existing intent if one was already created.
            if (job.getStripePaymentIntentId() != null) {
                PaymentIntent existing = PaymentIntent.retrieve(job.getStripePaymentIntentId());
                if (existing.getClientSecret() != null) {
                    log.info("Returning existing PaymentIntent {} for job {}", existing.getId(), jobId);
                    return existing.getClientSecret();
                }
            }

            // Convert CAD to cents for Stripe.
            long amountCents = Math.round(job.getTotalAmountCAD() * 100);

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency(CURRENCY_CAD)
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                    .putMetadata("jobId",       jobId)
                    .putMetadata("requesterId", job.getRequesterId())
                    .build();

            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("pi_create_" + jobId)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params, options);

            // Persist the intent ID and client secret on the job document.
            Map<String, Object> updates = new HashMap<>();
            updates.put("stripePaymentIntentId",           intent.getId());
            updates.put("stripePaymentIntentClientSecret", intent.getClientSecret());
            updates.put("updatedAt",                       Timestamp.now());
            firestore.collection(JOBS_COLLECTION).document(jobId).update(updates).get();

            log.info("PaymentIntent {} created for job {} ({}¢ CAD)", intent.getId(), jobId, amountCents);
            return intent.getClientSecret();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (StripeException e) {
            log.error("Stripe error creating intent for job {}: {}", jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment provider error — please try again");
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create payment intent");
        }
    }

    /**
     * Captures a previously authorized PaymentIntent, moving funds from the
     * customer's card authorization into the platform's Stripe balance.
     *
     * Called by {@link com.yosnowmow.controller.WebhookController} when Stripe fires
     * {@code payment_intent.amount_capturable_updated}.
     *
     * @param paymentIntentId the Stripe PaymentIntent ID (e.g. {@code pi_3PxYZ...})
     */
    public void capturePayment(String paymentIntentId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId);
            intent.capture();
            log.info("PaymentIntent {} captured successfully", paymentIntentId);
        } catch (StripeException e) {
            log.error("Stripe error capturing intent {}: {}", paymentIntentId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment capture failed: " + e.getMessage());
        }
    }

    /**
     * Returns the Stripe webhook endpoint secret, used by
     * {@link com.yosnowmow.controller.WebhookController} to verify event signatures.
     *
     * @return webhook secret value
     */
    public String getWebhookSecret() {
        return webhookSecret;
    }

    // ── Worker payouts — P1-12 ────────────────────────────────────────────────

    /**
     * Creates or retrieves a Stripe Connect Express account for the Worker and
     * returns a one-time onboarding link.
     *
     * If the worker already has a {@code stripeConnectAccountId} a fresh
     * AccountLink is created for the same account (onboarding re-entry / refresh).
     *
     * @param workerUid  Firebase UID of the Worker
     * @param returnUrl  URL to redirect to after successful onboarding
     * @param refreshUrl URL to redirect to if the link expires or is reused
     * @return Stripe-hosted onboarding URL (valid for a few minutes)
     */
    public String createConnectOnboardingLink(String workerUid,
                                              String returnUrl,
                                              String refreshUrl) {
        try {
            DocumentSnapshot workerDoc = firestore.collection(USERS_COLLECTION)
                    .document(workerUid).get().get();

            if (!workerDoc.exists()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Worker not found: " + workerUid);
            }

            User workerUser = workerDoc.toObject(User.class);
            if (workerUser == null || workerUser.getWorker() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "User has no active Worker profile");
            }

            String accountId = workerUser.getWorker().getStripeConnectAccountId();

            if (accountId == null || accountId.isBlank()) {
                // Create a new Express account for this worker.
                AccountCreateParams accountParams = AccountCreateParams.builder()
                        .setType(AccountCreateParams.Type.EXPRESS)
                        .setCountry(CONNECT_COUNTRY)
                        .setEmail(workerUser.getEmail())
                        .putMetadata("workerUid", workerUid)
                        .build();

                Account account = Account.create(accountParams);
                accountId = account.getId();

                // Persist the Stripe account ID and update onboarding status.
                Map<String, Object> updates = new HashMap<>();
                updates.put("worker.stripeConnectAccountId", accountId);
                updates.put("worker.stripeConnectStatus",    "pending");
                updates.put("updatedAt",                     Timestamp.now());
                firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();

                log.info("Stripe Connect account {} created for worker {}", accountId, workerUid);
            }

            // Generate a fresh onboarding link.
            AccountLinkCreateParams linkParams = AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .setReturnUrl(returnUrl)
                    .setRefreshUrl(refreshUrl)
                    .build();

            AccountLink link = AccountLink.create(linkParams);
            log.info("Onboarding link created for worker {} (account {})", workerUid, accountId);
            return link.getUrl();

        } catch (ResponseStatusException e) {
            throw e;
        } catch (StripeException e) {
            log.error("Stripe error creating Connect link for worker {}: {}", workerUid,
                    e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Could not create onboarding link");
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to create onboarding link");
        }
    }

    /**
     * Transfers the Worker's payout from the platform Stripe balance to their
     * Connected account.
     *
     * Amount transferred = {@code workerPayoutCAD} (already net of platform commission).
     * HST collected from the Requester is included in the transfer — the Worker remits
     * HST to CRA directly.
     *
     * The job is transitioned to RELEASED after a successful transfer.
     * Idempotent via Stripe idempotency key {@code "transfer_" + jobId}.
     *
     * @param jobId Firestore document ID of the completed job
     */
    public void releasePayment(String jobId) {
        try {
            Job job = jobService.getJob(jobId);

            if (job.getWorkerId() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Job has no assigned Worker");
            }
            if (job.getWorkerPayoutCAD() == null) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Worker payout amount not set on job");
            }

            // Fetch the Worker's Stripe Connect account ID.
            DocumentSnapshot workerDoc = firestore.collection(USERS_COLLECTION)
                    .document(job.getWorkerId()).get().get();

            User workerUser = workerDoc.exists() ? workerDoc.toObject(User.class) : null;
            String connectAccountId = (workerUser != null && workerUser.getWorker() != null)
                    ? workerUser.getWorker().getStripeConnectAccountId()
                    : null;

            if (connectAccountId == null || connectAccountId.isBlank()) {
                log.error("Worker {} has no Stripe Connect account — cannot release payment for job {}",
                        job.getWorkerId(), jobId);
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Worker has not completed Stripe Connect onboarding");
            }

            // Transfer amount = workerPayoutCAD (commission already deducted by DispatchService).
            // HST is included in the payout; the worker remits to CRA themselves.
            long payoutCents = Math.round(job.getWorkerPayoutCAD() * 100);

            // Also transfer HST portion if worker is HST registered.
            if (job.getHstAmountCAD() != null) {
                payoutCents += Math.round(job.getHstAmountCAD() * 100);
            }

            TransferCreateParams transferParams = TransferCreateParams.builder()
                    .setAmount(payoutCents)
                    .setCurrency(CURRENCY_CAD)
                    .setDestination(connectAccountId)
                    .setTransferGroup(jobId)
                    .putMetadata("jobId",    jobId)
                    .putMetadata("workerId", job.getWorkerId())
                    .build();

            RequestOptions options = RequestOptions.builder()
                    .setIdempotencyKey("transfer_" + jobId)
                    .build();

            Transfer transfer = Transfer.create(transferParams, options);

            Map<String, Object> extras = new HashMap<>();
            extras.put("stripeTransferId", transfer.getId());
            extras.put("releasedAt",       Timestamp.now());
            jobService.transitionStatus(jobId, "RELEASED", "system", extras);

            log.info("Transfer {} ({} ¢ CAD) released to worker {} for job {}",
                    transfer.getId(), payoutCents, job.getWorkerId(), jobId);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (StripeException e) {
            log.error("Stripe error releasing payment for job {}: {}", jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Payment release failed");
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to release payment");
        }
    }

    /**
     * Refunds the Requester for a cancelled or disputed job.
     *
     * Cancels the Stripe PaymentIntent if it is still capturable; otherwise creates
     * a full Refund.  The job is transitioned to REFUNDED.
     *
     * @param jobId Firestore document ID of the job to refund
     */
    public void refundJob(String jobId) {
        try {
            Job job = jobService.getJob(jobId);

            if (job.getStripePaymentIntentId() == null) {
                // No payment was ever made — just update the status.
                jobService.transitionStatus(jobId, "REFUNDED", "system", null);
                return;
            }

            PaymentIntent intent = PaymentIntent.retrieve(job.getStripePaymentIntentId());
            String piStatus = intent.getStatus();

            if ("requires_capture".equals(piStatus)) {
                // Funds authorized but not captured — cancel the authorization (free).
                intent.cancel();
                log.info("PaymentIntent {} cancelled for job {} refund", intent.getId(), jobId);
            } else if ("succeeded".equals(piStatus)) {
                // Funds already captured — create a full refund.
                RefundCreateParams refundParams = RefundCreateParams.builder()
                        .setPaymentIntent(intent.getId())
                        .putMetadata("jobId", jobId)
                        .build();

                Refund refund = Refund.create(refundParams);
                log.info("Refund {} created for job {} (intent {})",
                        refund.getId(), jobId, intent.getId());
            } else {
                log.warn("PaymentIntent {} for job {} is in unexpected status {} — skipping",
                        intent.getId(), jobId, piStatus);
            }

            Map<String, Object> extras = new HashMap<>();
            extras.put("refundedAt", Timestamp.now());
            jobService.transitionStatus(jobId, "REFUNDED", "system", extras);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (StripeException e) {
            log.error("Stripe error refunding job {}: {}", jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Refund failed");
        }
    }
}
