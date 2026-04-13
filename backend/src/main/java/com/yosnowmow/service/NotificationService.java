package com.yosnowmow.service;

// ============================================================
// IMPORTANT — Domain verification required before emails reach recipients:
//
// 1. Go to SendGrid → Settings → Sender Authentication → Authenticate Your Domain
// 2. Add the SPF and DKIM DNS records provided by SendGrid to your domain registrar
// 3. Add a DMARC record to your domain:
//       _dmarc.yosnowmow.com  TXT  "v=DMARC1; p=none; rua=mailto:dmarc@yosnowmow.com"
// 4. In SendGrid, verify the domain before going to production
//
// Until domain verification is complete, emails may land in spam or bounce.
// ============================================================

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.yosnowmow.model.Address;
import com.yosnowmow.model.Job;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Sends transactional emails via SendGrid (P1-17) and push notifications via
 * Firebase Cloud Messaging (P1-18 — stub methods below are wired in P1-18).
 *
 * <h3>Design rules</h3>
 * <ul>
 *   <li>Every public method is {@code @Async} — callers never wait for email delivery.</li>
 *   <li>Every send is wrapped in try-catch — email failure never propagates to the caller.</li>
 *   <li>If the SendGrid API key starts with "placeholder", all sends are skipped and logged
 *       at DEBUG level (dev environment with no real key configured).</li>
 *   <li>User email addresses are resolved from Firebase Auth — the authoritative source.</li>
 * </ul>
 *
 * <h3>Call sites</h3>
 * <ul>
 *   <li>{@code sendWelcomeEmail}       — {@code UserService.createUser()}</li>
 *   <li>{@code sendJobConfirmedEmail}   — {@code WebhookController.handlePaymentSucceeded()}</li>
 *   <li>{@code sendJobInProgressEmail}  — {@code JobService} on IN_PROGRESS transition</li>
 *   <li>{@code sendJobCompleteEmail}    — {@code JobService} on COMPLETE transition</li>
 *   <li>{@code sendPayoutReleasedEmail} — {@code PaymentService.releasePayment()}</li>
 *   <li>{@code sendDisputeOpenedEmail}  — {@code DisputeService.openDispute()}</li>
 *   <li>{@code sendDisputeResolvedEmail}— {@code DisputeService.resolveDispute()}</li>
 *   <li>{@code sendCancellationEmail}   — {@code JobService} on CANCELLED transition</li>
 *   <li>{@code notifyRequesterNoWorkers}— {@code DispatchService.cancelJobNoWorkers()}</li>
 *   <li>{@code notifyPaymentFailed}     — {@code WebhookController.handlePaymentFailed()}</li>
 * </ul>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Admin email for dispute adjudication notices. Configure via ADMIN_EMAIL in Phase 2. */
    private static final String ADMIN_EMAIL = "admin@yosnowmow.com";

    @Value("${yosnowmow.sendgrid.api-key}")
    private String sendgridApiKey;

    @Value("${yosnowmow.sendgrid.from-email}")
    private String fromEmail;

    @Value("${yosnowmow.sendgrid.from-name}")
    private String fromName;

    /**
     * BCC address added to every outgoing email.
     * Configured via {@code ADMIN_BCC_EMAIL} environment variable; defaults to
     * {@code perelgut@gmail.com}.  Empty string disables BCC.
     */
    @Value("${yosnowmow.sendgrid.bcc-email:}")
    private String bccEmail;

    /** Firestore collection names for the notifications in-app feed. */
    private static final String USERS_COLLECTION         = "users";
    private static final String NOTIFICATIONS_COLLECTION = "notifications";
    private static final String FEED_COLLECTION          = "feed";

    private final FirebaseAuth       firebaseAuth;
    private final FirebaseMessaging  firebaseMessaging;
    private final Firestore          firestore;
    private SendGrid sendGridClient;

    public NotificationService(FirebaseAuth firebaseAuth,
                               FirebaseMessaging firebaseMessaging,
                               Firestore firestore) {
        this.firebaseAuth      = firebaseAuth;
        this.firebaseMessaging = firebaseMessaging;
        this.firestore         = firestore;
    }

    @PostConstruct
    public void init() {
        sendGridClient = new SendGrid(sendgridApiKey);
    }

    // ── P1-18 push-notification methods ──────────────────────────────────────

    /**
     * Sends a job-offer push notification to a Worker.
     * Called by {@code DispatchService} when a new job request is sent to a Worker.
     *
     * @param workerUid  Firebase UID of the Worker
     * @param jobId      Firestore job document ID
     * @param address    human-readable property address
     * @param payoutCAD  estimated Worker payout in CAD
     */
    @Async
    public void sendJobRequest(String workerUid, String jobId,
                               String address, double payoutCAD) {
        sendPush(workerUid, "JOB_REQUEST",
                "New snow-clearing job nearby",
                String.format("Job at %s — Est. payout %s. Tap to review.", address, formatCad(payoutCAD)),
                Map.of("jobId", jobId));
    }

    /**
     * Notifies a Requester (push + email) that a Worker has accepted their job.
     * The email prompts payment; the push provides an immediate alert.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       Firestore job document ID
     * @param workerId    Firebase UID of the accepting Worker
     */
    @Async
    public void notifyRequesterJobAccepted(String requesterId, String jobId, String workerId) {
        // Email
        String email = lookupEmail(requesterId);
        if (email != null) {
            sendEmail(email, "A worker accepted your YoSnowMow job — complete payment to confirm",
                    buildHtml("Worker Accepted", """
                            <p>Great news — a worker has accepted your snow-clearing job.</p>
                            <p>Your job is now in <strong>Pending Deposit</strong> status.
                            Please log in and complete your payment within 30 minutes to confirm
                            the booking before it expires.</p>
                            <p>Job ID: <strong>%s</strong></p>
                            """.formatted(jobId)));
        }
        // Push
        sendPush(requesterId, "JOB_ACCEPTED",
                "Worker accepted — complete payment",
                "A worker is ready. Complete your payment within 30 minutes to confirm.",
                Map.of("jobId", jobId));
    }

    /**
     * Notifies both Requester and Worker (push) that the job is confirmed and payment cleared.
     * The full confirmation email is handled separately by {@link #sendJobConfirmedEmail}.
     *
     * @param requesterId Firebase UID of the Requester
     * @param workerId    Firebase UID of the Worker
     * @param jobId       Firestore job document ID
     * @param address     human-readable property address
     */
    @Async
    public void notifyJobConfirmed(String requesterId, String workerId,
                                   String jobId, String address) {
        sendPush(requesterId, "JOB_CONFIRMED",
                "Your job is confirmed!",
                "Payment received. Your worker will head to " + address + " shortly.",
                Map.of("jobId", jobId));
        sendPush(workerId, "JOB_CONFIRMED",
                "Job confirmed — head to " + address,
                "Payment cleared. Mark the job as In Progress when you arrive.",
                Map.of("jobId", jobId));
    }

    /**
     * Notifies the Requester (push) that the Worker has arrived and started.
     * The in-progress email is handled separately by {@link #sendJobInProgressEmail}.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       Firestore job document ID
     * @param address     human-readable property address
     */
    @Async
    public void notifyWorkerArrived(String requesterId, String jobId, String address) {
        sendPush(requesterId, "WORKER_ARRIVED",
                "Your worker has arrived!",
                "Snow clearing is now in progress at " + address + ".",
                Map.of("jobId", jobId));
    }

    /**
     * Notifies the Requester (push) that their job is complete.
     * Also prompts them to rate the worker.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       Firestore job document ID
     */
    @Async
    public void notifyJobCompleteRequester(String requesterId, String jobId) {
        sendPush(requesterId, "JOB_COMPLETE",
                "Job complete — please rate your worker",
                "Your driveway is cleared! Tap to rate and release payment.",
                Map.of("jobId", jobId));
    }

    /**
     * Notifies the Worker (push) that the job is complete and their payout is pending.
     *
     * @param workerId  Firebase UID of the Worker
     * @param jobId     Firestore job document ID
     * @param payoutCAD Worker payout amount in CAD
     */
    @Async
    public void notifyJobCompleteWorker(String workerId, String jobId, double payoutCAD) {
        sendPush(workerId, "JOB_COMPLETE",
                "Job marked complete",
                String.format("Your payout of %s is pending release. Please rate your requester.",
                        formatCad(payoutCAD)),
                Map.of("jobId", jobId));
    }

    /**
     * Prompts a user (push) to submit a rating.
     * Called after COMPLETE when a rating reminder is due.
     *
     * @param uid   Firebase UID of the user to remind
     * @param jobId Firestore job document ID
     */
    @Async
    public void notifyRatingRequest(String uid, String jobId) {
        sendPush(uid, "RATING_REQUEST",
                "Please rate your experience",
                "Your rating helps the YoSnowMow community. Tap to submit.",
                Map.of("jobId", jobId));
    }

    /**
     * Notifies the Worker (push) that their Stripe payout has been released.
     * The payout email is handled separately by {@link #sendPayoutReleasedEmail}.
     *
     * @param workerId  Firebase UID of the Worker
     * @param payoutCAD payout amount in CAD
     * @param jobId     Firestore job document ID
     */
    @Async
    public void notifyPayoutReleased(String workerId, double payoutCAD, String jobId) {
        sendPush(workerId, "PAYOUT_RELEASED",
                "Your payout has been released",
                String.format("%s is on its way to your Stripe account (2–3 business days).",
                        formatCad(payoutCAD)),
                Map.of("jobId", jobId));
    }

    /**
     * Notifies a user (push) that a dispute has been opened on their job.
     * Call once for the Requester and once for the Worker.
     *
     * @param uid   Firebase UID of the user to notify
     * @param jobId Firestore job document ID
     * @param role  "requester" or "worker" (used to tailor the message)
     */
    @Async
    public void notifyDisputeOpened(String uid, String jobId, String role) {
        String body = "requester".equals(role)
                ? "Your dispute has been received. An admin will review it within 1–2 business days."
                : "A dispute has been filed on job " + jobId + ". An admin will contact you.";
        sendPush(uid, "DISPUTE_OPENED",
                "Dispute opened — Job " + jobId,
                body,
                Map.of("jobId", jobId));
    }

    /**
     * Notifies a user (push) of the dispute resolution outcome.
     * Call once for the Requester and once for the Worker.
     *
     * @param uid        Firebase UID of the user to notify
     * @param jobId      Firestore job document ID
     * @param resolution "release" | "refund" | "split"
     */
    @Async
    public void notifyDisputeResolved(String uid, String jobId, String resolution) {
        String body = switch (resolution.toLowerCase()) {
            case "refund" -> "Dispute resolved: a full refund has been issued.";
            case "split"  -> "Dispute resolved: a partial refund/payout split has been applied.";
            default       -> "Dispute resolved: payment has been released to the worker.";
        };
        sendPush(uid, "DISPUTE_RESOLVED",
                "Dispute resolved — Job " + jobId,
                body,
                Map.of("jobId", jobId));
    }

    /**
     * Notifies a user (push) that a job has been cancelled.
     * Call once for the Requester and (if assigned) once for the Worker.
     *
     * @param uid        Firebase UID of the user to notify
     * @param jobId      Firestore job document ID
     * @param feeCharged true if the $10 cancellation fee applies (Requester only)
     */
    @Async
    public void notifyCancellation(String uid, String jobId, boolean feeCharged) {
        String body = feeCharged
                ? "Your job has been cancelled. A $10.00 cancellation fee was applied."
                : "Your job has been cancelled. No fee was charged.";
        sendPush(uid, "JOB_CANCELLED",
                "Job cancelled",
                body,
                Map.of("jobId", jobId));
    }

    // ── P1-17 email methods ───────────────────────────────────────────────────

    /**
     * Sends a welcome email when a new user registers.
     * Called by {@code UserService.createUser()} after the Firestore user document is written.
     *
     * @param uid         Firebase UID of the new user
     * @param displayName user's display name
     * @param role        "REQUESTER" or "WORKER"
     */
    @Async
    public void sendWelcomeEmail(String uid, String displayName, String role) {
        String email = lookupEmail(uid);
        if (email == null) return;

        String roleNote = "WORKER".equalsIgnoreCase(role)
                ? "<p>As a <strong>Worker</strong>, set up your profile, add your pricing tiers, "
                  + "and start receiving job requests from neighbours nearby.</p>"
                : "<p>As a <strong>Requester</strong>, post a job whenever you need your driveway "
                  + "or sidewalk cleared — we'll find an available worker near you.</p>";

        sendEmail(email, "Welcome to YoSnowMow, " + displayName + "!",
                buildHtml("Welcome to YoSnowMow!", """
                        <p>Hi %s,</p>
                        <p>Welcome to YoSnowMow — connecting Ontario property owners with nearby
                        snowblower owners.</p>
                        %s
                        <p>If you have any questions, just reply to this email.</p>
                        """.formatted(displayName, roleNote)));
    }

    /**
     * Sends confirmation emails to both the Requester and Worker once payment clears.
     * Called by {@code WebhookController.handlePaymentSucceeded()} after the job reaches CONFIRMED.
     *
     * @param requesterId Firebase UID of the Requester
     * @param workerId    Firebase UID of the Worker
     * @param job         the confirmed job document
     */
    @Async
    public void sendJobConfirmedEmail(String requesterId, String workerId, Job job) {
        String requesterEmail = lookupEmail(requesterId);
        String workerEmail    = lookupEmail(workerId);
        String address        = formatAddress(job.getPropertyAddress());
        String total          = formatCad(job.getTotalAmountCAD());
        String payout         = formatCad(job.getWorkerPayoutCAD());
        String jobId          = job.getJobId();

        if (requesterEmail != null) {
            sendEmail(requesterEmail, "Your YoSnowMow job is confirmed!",
                    buildHtml("Job Confirmed", """
                            <p>Your snow-clearing job has been confirmed and payment received.</p>
                            <table>
                              <tr><td><strong>Address:</strong></td><td>%s</td></tr>
                              <tr><td><strong>Total charged:</strong></td><td>%s</td></tr>
                              <tr><td><strong>Job ID:</strong></td><td>%s</td></tr>
                            </table>
                            <p>Your worker will be on the way. You'll receive another email when the job
                            starts.</p>
                            """.formatted(address, total, jobId)));
        }

        if (workerEmail != null) {
            sendEmail(workerEmail, "Job confirmed — head to " + address,
                    buildHtml("Job Confirmed — You're Up!", """
                            <p>A job has been confirmed and is ready for you.</p>
                            <table>
                              <tr><td><strong>Address:</strong></td><td>%s</td></tr>
                              <tr><td><strong>Your payout:</strong></td><td>%s</td></tr>
                              <tr><td><strong>Job ID:</strong></td><td>%s</td></tr>
                            </table>
                            <p>Head to the property and mark the job as <strong>In Progress</strong>
                            when you arrive.</p>
                            """.formatted(address, payout, jobId)));
        }
    }

    /**
     * Notifies the Requester that the Worker has arrived and started the job.
     * Called by {@code JobService} when a job transitions to IN_PROGRESS.
     *
     * @param requesterId Firebase UID of the Requester
     * @param job         the job document
     */
    @Async
    public void sendJobInProgressEmail(String requesterId, Job job) {
        String email = lookupEmail(requesterId);
        if (email == null) return;
        sendEmail(email, "Your worker has started the job!",
                buildHtml("Job In Progress", """
                        <p>Your worker has arrived at <strong>%s</strong> and started clearing
                        your snow.</p>
                        <p>You'll receive a confirmation once the job is complete.</p>
                        <p>Job ID: <strong>%s</strong></p>
                        """.formatted(formatAddress(job.getPropertyAddress()), job.getJobId())));
    }

    /**
     * Sends completion emails to both parties when a job reaches COMPLETE.
     * Reminds both to submit a rating — rating by both parties triggers immediate payout.
     * Called by {@code JobService} when a job transitions to COMPLETE.
     *
     * @param requesterId Firebase UID of the Requester
     * @param workerId    Firebase UID of the Worker
     * @param job         the completed job document
     */
    @Async
    public void sendJobCompleteEmail(String requesterId, String workerId, Job job) {
        String requesterEmail = lookupEmail(requesterId);
        String workerEmail    = lookupEmail(workerId);
        String address        = formatAddress(job.getPropertyAddress());
        String payout         = formatCad(job.getWorkerPayoutCAD());
        String jobId          = job.getJobId();

        if (requesterEmail != null) {
            sendEmail(requesterEmail, "Your snow-clearing job is complete!",
                    buildHtml("Job Complete", """
                            <p>Your job at <strong>%s</strong> has been marked complete.</p>
                            <p>Please log in to <strong>rate your worker</strong> — your rating helps the
                            community. If you have a concern about the quality of work, you have
                            <strong>2 hours</strong> to open a dispute.</p>
                            <p>Job ID: <strong>%s</strong></p>
                            """.formatted(address, jobId)));
        }

        if (workerEmail != null) {
            sendEmail(workerEmail, "Job complete — payout on its way",
                    buildHtml("Job Complete", """
                            <p>The job at <strong>%s</strong> is marked complete.</p>
                            <p>Your payout of <strong>%s</strong> will be released once both parties have
                            rated the job, or automatically after 4 hours if the requester does not act.</p>
                            <p>Please log in to rate your requester.</p>
                            <p>Job ID: <strong>%s</strong></p>
                            """.formatted(address, payout, jobId)));
        }
    }

    /**
     * Notifies the Worker that their Stripe payout has been initiated.
     * Called by {@code PaymentService.releasePayment()} after the Stripe transfer is created.
     *
     * @param workerId  Firebase UID of the Worker
     * @param amountCAD payout amount in CAD (e.g. 40.80)
     * @param jobId     Firestore job document ID
     */
    @Async
    public void sendPayoutReleasedEmail(String workerId, double amountCAD, String jobId) {
        String email = lookupEmail(workerId);
        if (email == null) return;
        sendEmail(email, "Your YoSnowMow payout has been released",
                buildHtml("Payout Released", """
                        <p>Your payment of <strong>%s</strong> for job <strong>%s</strong> has been
                        released.</p>
                        <p>Funds will arrive in your Stripe Connect account within
                        <strong>2&ndash;3 business days</strong> depending on your payout schedule.</p>
                        """.formatted(formatCad(amountCAD), jobId)));
    }

    /**
     * Notifies both parties and the admin when a dispute is opened.
     * Called by {@code DisputeService.openDispute()} when a job transitions to DISPUTED.
     *
     * @param requesterId Firebase UID of the Requester
     * @param workerId    Firebase UID of the Worker
     * @param jobId       Firestore job document ID
     */
    @Async
    public void sendDisputeOpenedEmail(String requesterId, String workerId, String jobId) {
        String requesterEmail = lookupEmail(requesterId);
        String workerEmail    = lookupEmail(workerId);

        if (requesterEmail != null) {
            sendEmail(requesterEmail, "Your dispute has been received — Job " + jobId,
                    buildHtml("Dispute Opened", """
                            <p>You have opened a dispute for job <strong>%s</strong>.</p>
                            <p>A YoSnowMow administrator will review the case and may contact you for
                            more information. Payment is held in escrow until the dispute is resolved.
                            Reviews typically take 1&ndash;2 business days.</p>
                            """.formatted(jobId)));
        }

        if (workerEmail != null) {
            sendEmail(workerEmail, "Dispute notice — Job " + jobId,
                    buildHtml("Dispute Notice", """
                            <p>The requester for job <strong>%s</strong> has opened a dispute.</p>
                            <p>A YoSnowMow administrator will review the case and may contact you for
                            more information. Payment is held in escrow until the dispute is resolved.</p>
                            """.formatted(jobId)));
        }

        // Alert admin — always sent regardless of whether parties' addresses resolved.
        sendEmail(ADMIN_EMAIL, "[Action Required] New dispute — Job " + jobId,
                buildHtml("Dispute Requires Adjudication", """
                        <p>A dispute has been opened and requires admin review.</p>
                        <table>
                          <tr><td><strong>Job ID:</strong></td><td>%s</td></tr>
                          <tr><td><strong>Requester UID:</strong></td><td>%s</td></tr>
                          <tr><td><strong>Worker UID:</strong></td><td>%s</td></tr>
                        </table>
                        <p>Please log in to the admin dashboard to adjudicate.</p>
                        """.formatted(jobId, requesterId, workerId)));
    }

    /**
     * Notifies both parties of the dispute resolution outcome.
     * Called by {@code DisputeService.resolveDispute()} after the admin adjudicates.
     *
     * @param requesterId Firebase UID of the Requester
     * @param workerId    Firebase UID of the Worker
     * @param resolution  "release" | "refund" | "split"
     * @param job         the resolved job document
     */
    @Async
    public void sendDisputeResolvedEmail(String requesterId, String workerId,
                                          String resolution, Job job) {
        String requesterEmail = lookupEmail(requesterId);
        String workerEmail    = lookupEmail(workerId);
        String jobId          = job.getJobId();

        String outcomeRequester = switch (resolution.toLowerCase()) {
            case "refund" -> "The dispute has been resolved in your favour. A full refund of <strong>"
                    + formatCad(job.getTotalAmountCAD())
                    + "</strong> will be returned to your original payment method within 5&ndash;10 business days.";
            case "split"  -> "The dispute has been resolved with a split outcome. A partial refund "
                    + "will be issued; the remainder was released to the worker.";
            default       -> "The dispute has been resolved. Payment of <strong>"
                    + formatCad(job.getTotalAmountCAD())
                    + "</strong> has been released to the worker.";
        };

        String outcomeWorker = switch (resolution.toLowerCase()) {
            case "refund" -> "The dispute has been resolved in the requester's favour. "
                    + "The payment for this job has been refunded and no payout will be issued.";
            case "split"  -> "The dispute has been resolved with a split outcome. A partial payout "
                    + "will be issued to your Stripe Connect account within 2&ndash;3 business days.";
            default       -> "The dispute has been resolved in your favour. Your payout of <strong>"
                    + formatCad(job.getWorkerPayoutCAD())
                    + "</strong> has been released and will arrive in 2&ndash;3 business days.";
        };

        if (requesterEmail != null) {
            sendEmail(requesterEmail, "Dispute resolved — Job " + jobId,
                    buildHtml("Dispute Resolved", "<p>" + outcomeRequester
                            + "</p><p>Job ID: <strong>" + jobId + "</strong></p>"));
        }
        if (workerEmail != null) {
            sendEmail(workerEmail, "Dispute resolved — Job " + jobId,
                    buildHtml("Dispute Resolved", "<p>" + outcomeWorker
                            + "</p><p>Job ID: <strong>" + jobId + "</strong></p>"));
        }
    }

    /**
     * Sends cancellation emails to both parties.
     * Called by {@code JobService} when a job transitions to CANCELLED.
     *
     * @param requesterId Firebase UID of the Requester
     * @param workerId    Firebase UID of the Worker (may be null if cancelled before CONFIRMED)
     * @param feeCharged  true if the $10 cancellation fee was applied
     * @param feeCAD      cancellation fee amount in CAD (0.0 if not charged)
     * @param jobId       Firestore job document ID
     */
    @Async
    public void sendCancellationEmail(String requesterId, String workerId,
                                       boolean feeCharged, double feeCAD, String jobId) {
        String requesterEmail = lookupEmail(requesterId);
        String feeNote = feeCharged
                ? " A cancellation fee of <strong>" + formatCad(feeCAD)
                  + "</strong> has been charged because the job was confirmed before cancellation."
                : " No cancellation fee was charged.";

        if (requesterEmail != null) {
            sendEmail(requesterEmail, "Your YoSnowMow job has been cancelled",
                    buildHtml("Job Cancelled", """
                            <p>Your snow-clearing job (<strong>%s</strong>) has been cancelled.%s</p>
                            <p>You are welcome to post a new job at any time.</p>
                            """.formatted(jobId, feeNote)));
        }

        if (workerId != null) {
            String workerEmail = lookupEmail(workerId);
            if (workerEmail != null) {
                sendEmail(workerEmail, "Job cancelled — " + jobId,
                        buildHtml("Job Cancelled", """
                                <p>The job <strong>%s</strong> has been cancelled by the requester.
                                No further action is required from you.</p>
                                """.formatted(jobId)));
            }
        }
    }

    /**
     * Notifies the Requester that no Workers were available and the job was cancelled.
     * Called by {@code DispatchService.cancelJobNoWorkers()}.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       Firestore job document ID
     */
    @Async
    public void notifyRequesterNoWorkers(String requesterId, String jobId) {
        String email = lookupEmail(requesterId);
        if (email == null) return;
        sendEmail(email, "We could not find a worker for your YoSnowMow job",
                buildHtml("No Workers Available", """
                        <p>Unfortunately, no available workers were found for your snow-clearing job
                        at this time. Your job (<strong>%s</strong>) has been cancelled at no charge.</p>
                        <p>You are welcome to post a new job — more workers may be available later.</p>
                        """.formatted(jobId)));
    }

    /**
     * Notifies the Requester that their Stripe payment attempt failed.
     * Called by {@code WebhookController.handlePaymentFailed()}.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       Firestore job document ID
     */
    @Async
    public void notifyPaymentFailed(String requesterId, String jobId) {
        String email = lookupEmail(requesterId);
        if (email == null) return;
        sendEmail(email, "Action required: payment failed for your YoSnowMow job",
                buildHtml("Payment Failed", """
                        <p>Your payment attempt for job <strong>%s</strong> did not go through.</p>
                        <p>Please log in and try again with a different card before your booking
                        window expires.</p>
                        """.formatted(jobId)));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Sends a push notification via Firebase Cloud Messaging and writes the
     * notification to the user's in-app feed regardless of FCM token availability.
     *
     * <p>Flow:
     * <ol>
     *   <li>Read the user's {@code fcmToken} from Firestore.</li>
     *   <li>If a token is present, send an FCM message.</li>
     *   <li>Write a notification document to {@code notifications/{uid}/feed/{uuid}}
     *       so the React client can show an in-app notification bell.</li>
     * </ol>
     *
     * <p>All failures are logged and swallowed — push failures must never
     * propagate to the caller.
     *
     * @param uid       Firebase UID of the recipient
     * @param type      notification type string (e.g. "JOB_CONFIRMED")
     * @param title     push notification title
     * @param body      push notification body
     * @param data      additional key-value data for the client (e.g. {@code jobId})
     */
    private void sendPush(String uid, String type, String title, String body,
                          Map<String, String> data) {
        try {
            // 1. Read FCM token from Firestore user doc.
            com.google.cloud.firestore.DocumentSnapshot userDoc = firestore
                    .collection(USERS_COLLECTION).document(uid).get().get();
            String fcmToken = userDoc.getString("fcmToken");

            // 2. Send FCM push if a device token is registered.
            if (fcmToken != null && !fcmToken.isBlank()) {
                Message message = Message.builder()
                        .setToken(fcmToken)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .putAllData(data != null ? data : Map.of())
                        .build();

                String messageId = firebaseMessaging.send(message);
                log.debug("FCM sent to uid={} type={} messageId={}", uid, type, messageId);
            } else {
                log.debug("No FCM token for uid={} — skipping push for type={}", uid, type);
            }

            // 3. Write to in-app notification feed (always written, even without FCM).
            String notifId = UUID.randomUUID().toString();
            Map<String, Object> notif = new HashMap<>();
            notif.put("notifId",   notifId);
            notif.put("type",      type);
            notif.put("title",     title);
            notif.put("body",      body);
            notif.put("data",      data != null ? data : Map.of());
            notif.put("isRead",    false);
            notif.put("createdAt", Timestamp.now());

            firestore.collection(NOTIFICATIONS_COLLECTION)
                    .document(uid)
                    .collection(FEED_COLLECTION)
                    .document(notifId)
                    .set(notif)
                    .get();

        } catch (FirebaseMessagingException e) {
            // FCM token may be stale or revoked — log and continue.
            log.warn("FCM send failed for uid={} type={}: {}", uid, type, e.getMessage());
        } catch (Exception e) {
            log.error("sendPush failed for uid={} type={}: {}", uid, type, e.getMessage(), e);
        }
    }

    /**
     * Looks up a user's email from Firebase Auth (the authoritative source).
     * Returns {@code null} and logs an error if the lookup fails.
     */
    private String lookupEmail(String uid) {
        try {
            return firebaseAuth.getUser(uid).getEmail();
        } catch (Exception e) {
            log.error("Could not fetch email for uid {}: {}", uid, e.getMessage());
            return null;
        }
    }

    /**
     * Sends a transactional HTML email via SendGrid.
     * Logs and swallows all exceptions — email failure must never block a request.
     * Skips the send entirely if the API key is a placeholder (dev environment).
     */
    private void sendEmail(String toAddress, String subject, String htmlBody) {
        if (sendgridApiKey.startsWith("placeholder")) {
            log.debug("SendGrid not configured — skipping email to {} subject='{}'",
                    toAddress, subject);
            return;
        }
        try {
            Mail mail = new Mail(
                    new Email(fromEmail, fromName),
                    subject,
                    new Email(toAddress),
                    new Content("text/html", htmlBody));

            // BCC the admin address if configured (empty string disables it).
            if (bccEmail != null && !bccEmail.isBlank()) {
                mail.getPersonalization().get(0).addBcc(new Email(bccEmail));
            }

            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGridClient.api(request);
            if (response.getStatusCode() >= 400) {
                log.error("SendGrid {} for email to '{}' (subject='{}'): {}",
                        response.getStatusCode(), toAddress, subject, response.getBody());
            } else {
                log.info("Email sent to '{}' — '{}' (status {})",
                        toAddress, subject, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Failed to send email to '{}' (subject='{}'): {}",
                    toAddress, subject, e.getMessage(), e);
        }
    }

    /**
     * Wraps email content in a minimal, responsive HTML shell.
     *
     * @param heading display heading at the top of the email body
     * @param content inner HTML content (paragraphs, tables, etc.)
     * @return complete HTML document string ready to send
     */
    private static String buildHtml(String heading, String content) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <style>
                    body  { font-family: Arial, sans-serif; color: #333; background: #f4f4f4;
                            margin: 0; padding: 20px; }
                    .card { background: #fff; border-radius: 8px; padding: 32px;
                            max-width: 560px; margin: 0 auto; }
                    h2    { color: #1a73e8; margin-top: 0; }
                    table { border-collapse: collapse; margin: 12px 0; }
                    td    { padding: 4px 16px 4px 0; vertical-align: top; }
                    .footer { font-size: 12px; color: #888; margin-top: 24px;
                              border-top: 1px solid #eee; padding-top: 12px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h2>%s</h2>
                    %s
                    <div class="footer">
                      YoSnowMow &mdash; Connecting Ontario neighbours.<br>
                      Questions? Reply to this email or visit
                      <a href="https://yosnowmow.com">yosnowmow.com</a>.
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(heading, content);
    }

    /**
     * Formats a CAD dollar amount as {@code $X.XX CAD}.
     * Returns {@code "N/A"} if the value is null.
     */
    private static String formatCad(Double amountCAD) {
        if (amountCAD == null) return "N/A";
        return String.format("$%.2f CAD", amountCAD);
    }

    /**
     * Returns a human-readable address string.
     * Prefers {@code fullText}; falls back to structured field concatenation.
     */
    private static String formatAddress(Address address) {
        if (address == null) return "the property";
        if (address.getFullText() != null && !address.getFullText().isBlank()) {
            return address.getFullText();
        }
        StringBuilder sb = new StringBuilder();
        if (address.getStreetNumber() != null) sb.append(address.getStreetNumber()).append(' ');
        if (address.getStreet()       != null) sb.append(address.getStreet()).append(", ");
        if (address.getCity()         != null) sb.append(address.getCity()).append(", ");
        if (address.getProvince()     != null) sb.append(address.getProvince());
        String result = sb.toString().replaceAll(",\\s*$", "").trim();
        return result.isEmpty() ? "the property" : result;
    }
}
