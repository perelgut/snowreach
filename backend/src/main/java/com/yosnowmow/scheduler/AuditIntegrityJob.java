package com.yosnowmow.scheduler;

import com.yosnowmow.service.AuditLogService;
import com.yosnowmow.service.AuditLogService.IntegrityReport;
import com.yosnowmow.service.NotificationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that verifies the SHA-256 hash chain of the previous day's audit log
 * entries (P1-20).
 *
 * Fires at 02:00 America/Toronto every day, configured in
 * {@link com.yosnowmow.config.QuartzConfig}.
 *
 * On success: logs a summary line at INFO level.
 * On failure: logs CRITICAL-level details (one line per mismatch) and sends
 *             an alert email to the admin via {@link NotificationService}.
 *
 * {@code @DisallowConcurrentExecution} prevents multiple instances from running
 * simultaneously if a previous execution overruns its scheduled window.
 */
@DisallowConcurrentExecution
public class AuditIntegrityJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AuditIntegrityJob.class);

    /** Performs the hash-chain verification and returns a structured report. */
    @Autowired
    private AuditLogService auditLogService;

    /** Sends the admin alert email if any mismatches are found. */
    @Autowired
    private NotificationService notificationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("AuditIntegrityJob starting — verifying yesterday's audit entries");
        try {
            IntegrityReport report = auditLogService.verifyPreviousDay();

            if (report.errored()) {
                // The verification query itself failed — send alert so someone investigates
                log.error("AuditIntegrityJob ERRORED: verification query failed for {}",
                        report.getDate());
                notificationService.sendAuditIntegrityAlertEmail(report);

            } else if (!report.passed()) {
                // One or more hash mismatches — possible tampering
                log.error("AuditIntegrityJob FAILED: {}/{} mismatches for {}",
                        report.getMismatches(), report.getTotalChecked(), report.getDate());
                notificationService.sendAuditIntegrityAlertEmail(report);

            } else {
                log.info("AuditIntegrityJob PASSED: {} entries verified for {}",
                        report.getTotalChecked(), report.getDate());
            }

        } catch (Exception e) {
            // Should not reach here (verifyPreviousDay swallows its own errors),
            // but catch defensively so Quartz does not retry the job.
            log.error("AuditIntegrityJob threw unexpectedly: {}", e.getMessage(), e);
        }
    }
}
