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

import com.google.firebase.auth.FirebaseAuth;
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

    private final FirebaseAuth firebaseAuth;
    private SendGrid sendGridClient;

    public NotificationService(FirebaseAuth firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @PostConstruct
    public void init() {
        sendGridClient = new SendGrid(sendgridApiKey);
    }

    // ── Stub push-notification methods (wired in P1-18) ───────────────────────

    /**
     * Sends a job-offer push notification to a Worker.
     * TODO P1-18: wire to Firebase Cloud Messaging.
     *
     * @param workerUid Firebase UID of the Worker
     * @param jobId     Firestore job document ID
     */
    @Async
    public void sendJobRequest(String workerUid, String jobId) {
        log.debug("TODO P1-18 — sendJobRequest push: worker={} job={}", workerUid, jobId);
    }

    /**
     * Notifies a Requester that a Worker has accepted their job.
     * Full email confirmation is sent by {@link #sendJobConfirmedEmail} once payment clears;
     * this immediate notification can be wired to a push in P1-18.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       Firestore job document ID
     * @param workerId    Firebase UID of the accepting Worker
     */
    @Async
    public void notifyRequesterJobAccepted(String requesterId, String jobId, String workerId) {
        String email = lookupEmail(requesterId);
        if (email == null) return;
        sendEmail(email, "A worker accepted your YoSnowMow job — complete payment to confirm",
                buildHtml("Worker Accepted", """
                        <p>Great news — a worker has accepted your snow-clearing job.</p>
                        <p>Your job is now in <strong>Pending Deposit</strong> status.
                        Please log in and complete your payment within 30 minutes to confirm
                        the booking before it expires.</p>
                        <p>Job ID: <strong>%s</strong></p>
                        """.formatted(jobId)));
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
