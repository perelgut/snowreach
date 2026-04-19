package com.yosnowmow.scheduler;

import com.yosnowmow.service.AnalyticsService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Quartz job that runs the daily analytics data pipeline (P2-06).
 *
 * <p>Fires at 03:00 America/Toronto every day — one hour after
 * {@link AuditIntegrityJob} — configured in
 * {@link com.yosnowmow.config.QuartzConfig}.
 *
 * <p>On each execution this job:
 * <ol>
 *   <li>Computes aggregate statistics for the previous calendar day and
 *       writes them to {@code analyticsDaily/{YYYY-MM-DD}}.</li>
 *   <li>Increments the all-time totals in {@code analyticsSummary/current}.</li>
 *   <li>Deletes daily documents older than 90 days from {@code analyticsDaily}.</li>
 * </ol>
 *
 * <p>{@code @DisallowConcurrentExecution} prevents a second instance from
 * starting while a long-running execution is still in progress.
 */
@DisallowConcurrentExecution
public class AnalyticsJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsJob.class);

    private static final ZoneId ONTARIO_ZONE = ZoneId.of("America/Toronto");

    @Autowired
    private AnalyticsService analyticsService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {

        LocalDate yesterday = LocalDate.now(ONTARIO_ZONE).minusDays(1);
        log.info("AnalyticsJob starting — computing stats for {}", yesterday);

        try {
            // Step 1 + 2: aggregate stats and update summary.
            analyticsService.computeDailyStats(yesterday);

            // Step 3: prune documents older than 90 days.
            analyticsService.cleanupOldDailyStats();

            log.info("AnalyticsJob complete for {}", yesterday);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("AnalyticsJob interrupted for {}: {}", yesterday, e.getMessage(), e);
            // Re-throw as JobExecutionException so Quartz can record the failure.
            throw new JobExecutionException("AnalyticsJob interrupted", e, false);

        } catch (Exception e) {
            log.error("AnalyticsJob failed for {}: {}", yesterday, e.getMessage(), e);
            // false = do not re-fire immediately; let the next scheduled run retry.
            throw new JobExecutionException("AnalyticsJob failed for " + yesterday, e, false);
        }
    }
}
