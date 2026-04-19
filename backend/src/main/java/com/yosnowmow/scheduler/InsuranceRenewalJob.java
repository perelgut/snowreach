package com.yosnowmow.scheduler;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import com.yosnowmow.service.AuditLogService;
import com.yosnowmow.service.BadgeService;
import com.yosnowmow.service.InsuranceService;
import com.yosnowmow.service.NotificationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily Quartz job that enforces insurance expiry rules for Workers (P3-03).
 *
 * <p>Fires at 04:00 America/Toronto every day (scheduled in
 * {@link com.yosnowmow.config.QuartzConfig}).
 *
 * <h3>Logic per Worker</h3>
 * <ol>
 *   <li>Fetch all Workers whose {@code worker.insuranceStatus} is
 *       {@code VALID} or {@code EXPIRING_SOON}.</li>
 *   <li>Parse {@code worker.insurancePolicyExpiry} as a {@code LocalDate}.</li>
 *   <li>If expiry has passed today:
 *       <ul>
 *         <li>Set {@code insuranceStatus = EXPIRED}.</li>
 *         <li>Set {@code worker.isActive = false} (Worker cannot accept jobs).</li>
 *         <li>Send "insurance expired" email to the Worker.</li>
 *         <li>Write audit log {@code INSURANCE_EXPIRED}.</li>
 *       </ul>
 *   </li>
 *   <li>Else if expiry is within 30 days:
 *       <ul>
 *         <li>Set {@code insuranceStatus = EXPIRING_SOON} (idempotent if already set).</li>
 *         <li>Send renewal reminder email — but only if no reminder was sent in the
 *             last 7 days ({@code lastInsuranceReminderSent} field).</li>
 *         <li>Update {@code lastInsuranceReminderSent} after sending.</li>
 *         <li>Write audit log {@code INSURANCE_EXPIRING_SOON}.</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <p>{@code @DisallowConcurrentExecution} prevents two simultaneous runs if a previous
 * execution is still in flight when the next trigger fires.
 */
@Component
@DisallowConcurrentExecution
public class InsuranceRenewalJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(InsuranceRenewalJob.class);

    /** Ontario timezone — used to determine "today" consistently with other scheduled jobs. */
    private static final ZoneId ONTARIO_ZONE = ZoneId.of("America/Toronto");

    /** Days before expiry at which to start sending renewal reminders. */
    private static final int REMINDER_LEAD_DAYS = 30;

    /** Minimum days between consecutive renewal reminder emails. */
    private static final int REMINDER_INTERVAL_DAYS = 7;

    private static final String USERS_COLLECTION = "users";

    @Autowired private Firestore            firestore;
    @Autowired private NotificationService  notificationService;
    @Autowired private AuditLogService      auditLogService;
    @Autowired private BadgeService         badgeService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("InsuranceRenewalJob starting");

        LocalDate today = LocalDate.now(ONTARIO_ZONE);
        int expired  = 0;
        int reminded = 0;
        int errors   = 0;

        try {
            // Query all Workers with an insurance status that may require action.
            QuerySnapshot snap = firestore.collection(USERS_COLLECTION)
                    .whereIn("worker.insuranceStatus",
                            List.of(InsuranceService.STATUS_VALID,
                                    InsuranceService.STATUS_EXPIRING_SOON))
                    .get().get();

            log.info("InsuranceRenewalJob: {} Worker(s) to check", snap.size());

            for (QueryDocumentSnapshot doc : snap.getDocuments()) {
                String workerUid = doc.getId();
                try {
                    User user = doc.toObject(User.class);
                    if (user == null || user.getWorker() == null) continue;

                    WorkerProfile worker = user.getWorker();
                    String expiryStr = worker.getInsurancePolicyExpiry();
                    if (expiryStr == null || expiryStr.isBlank()) {
                        log.warn("Worker {} has insuranceStatus={} but no insurancePolicyExpiry — skipping",
                                workerUid, worker.getInsuranceStatus());
                        continue;
                    }

                    LocalDate expiryDate;
                    try {
                        expiryDate = LocalDate.parse(expiryStr); // ISO-8601 YYYY-MM-DD
                    } catch (Exception e) {
                        log.warn("Worker {} has unparseable insurancePolicyExpiry '{}' — skipping",
                                workerUid, expiryStr);
                        continue;
                    }

                    if (!expiryDate.isAfter(today)) {
                        // ── Policy expired ────────────────────────────────────
                        handleExpired(workerUid, expiryStr);
                        expired++;

                    } else if (!expiryDate.isAfter(today.plusDays(REMINDER_LEAD_DAYS))) {
                        // ── Expiring soon — potentially send reminder ─────────
                        handleExpiringSoon(workerUid, worker, expiryStr);
                        reminded++;
                    }

                } catch (Exception e) {
                    log.error("InsuranceRenewalJob: error processing workerUid={}: {}",
                            workerUid, e.getMessage(), e);
                    errors++;
                }
            }

        } catch (Exception e) {
            log.error("InsuranceRenewalJob: fatal query error: {}", e.getMessage(), e);
            throw new JobExecutionException("InsuranceRenewalJob failed: " + e.getMessage(),
                    e, false);
        }

        log.info("InsuranceRenewalJob complete — expired={} reminded={} errors={}",
                expired, reminded, errors);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Marks the Worker's insurance as EXPIRED, deactivates their account,
     * notifies them, and writes an audit entry.
     */
    private void handleExpired(String workerUid, String expiryStr)
            throws Exception {

        Map<String, Object> updates = new HashMap<>();
        updates.put("worker.insuranceStatus", InsuranceService.STATUS_EXPIRED);
        updates.put("worker.isActive",        false);
        updates.put("updatedAt",              Timestamp.now());

        firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();

        auditLogService.write("insurance-renewal-job", "INSURANCE_EXPIRED",
                "worker", workerUid,
                InsuranceService.STATUS_VALID, InsuranceService.STATUS_EXPIRED);

        notificationService.sendInsuranceExpired(workerUid);

        // Revoke INSURED badge — insurance is no longer valid.
        badgeService.evaluateBadges(workerUid);

        log.info("Worker {} insurance EXPIRED (expiry={}) — account deactivated",
                workerUid, expiryStr);
    }

    /**
     * Sets the Worker's status to EXPIRING_SOON and sends a renewal reminder,
     * subject to the 7-day minimum interval between reminders.
     */
    private void handleExpiringSoon(String workerUid, WorkerProfile worker,
                                    String expiryStr) throws Exception {

        // Build updates — always refresh the status to EXPIRING_SOON.
        Map<String, Object> updates = new HashMap<>();
        updates.put("worker.insuranceStatus", InsuranceService.STATUS_EXPIRING_SOON);
        updates.put("updatedAt",              Timestamp.now());

        // Check 7-day reminder gate.
        boolean shouldSendReminder = true;
        Timestamp lastSent = worker.getLastInsuranceReminderSent();
        if (lastSent != null) {
            long daysSinceLast = (Timestamp.now().toDate().getTime()
                    - lastSent.toDate().getTime()) / (1000L * 60 * 60 * 24);
            if (daysSinceLast < REMINDER_INTERVAL_DAYS) {
                shouldSendReminder = false;
                log.debug("Worker {} reminder suppressed — last sent {} day(s) ago",
                        workerUid, daysSinceLast);
            }
        }

        if (shouldSendReminder) {
            updates.put("worker.lastInsuranceReminderSent", Timestamp.now());
        }

        firestore.collection(USERS_COLLECTION).document(workerUid).update(updates).get();

        auditLogService.write("insurance-renewal-job", "INSURANCE_EXPIRING_SOON",
                "worker", workerUid,
                worker.getInsuranceStatus(), InsuranceService.STATUS_EXPIRING_SOON);

        if (shouldSendReminder) {
            notificationService.sendInsuranceRenewalReminder(workerUid, expiryStr);
            log.info("Worker {} insurance EXPIRING_SOON (expiry={}) — reminder sent",
                    workerUid, expiryStr);
        } else {
            log.info("Worker {} insurance EXPIRING_SOON (expiry={}) — reminder suppressed",
                    workerUid, expiryStr);
        }
    }
}
