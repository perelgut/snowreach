package com.yosnowmow.config;

import com.yosnowmow.scheduler.AnalyticsJob;
import com.yosnowmow.scheduler.AuditIntegrityJob;
import com.yosnowmow.scheduler.InsuranceRenewalJob;
import com.yosnowmow.scheduler.PostedJobExpiryJob;
import com.yosnowmow.scheduler.RejectionCountCleanupJob;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz Scheduler configuration.
 *
 * Spring Boot auto-configures Quartz when {@code spring-boot-starter-quartz} is
 * on the classpath.  The job-store type is controlled by the active Spring profile:
 * <ul>
 *   <li>{@code dev}  — {@code spring.quartz.job-store-type: memory} (application-dev.yml)</li>
 *   <li>{@code prod} — {@code spring.quartz.job-store-type: memory} (application-prod.yml,
 *       Phase 1; migrate to Cloud SQL JDBC store in Phase 2)</li>
 * </ul>
 *
 * Spring Boot also auto-configures {@code SpringBeanJobFactory}, which injects
 * Spring beans into Quartz {@link org.quartz.Job} instances via {@code @Autowired}.
 * No manual wiring is needed beyond declaring the {@link JobDetail} and {@link Trigger} beans.
 *
 * <h3>Registered cron jobs</h3>
 * <ul>
 *   <li>{@code auditIntegrityJob} — fires at 02:00 America/Toronto daily (P1-20).
 *       Verifies the previous day's audit log hash chain.</li>
 *   <li>{@code analyticsJob} — fires at 03:00 America/Toronto daily (P2-06).
 *       Aggregates the previous day's job, revenue, rating, and user stats.</li>
 *   <li>{@code insuranceRenewalJob} — fires at 04:00 America/Toronto daily (P3-03).
 *       Checks insurance expiry for all Workers; sends reminders / deactivates expired.</li>
 * </ul>
 *
 * Auto-release timers ({@link com.yosnowmow.scheduler.DisputeTimerJob}) are
 * scheduled dynamically by {@code JobService} rather than as static cron beans.
 *
 * Phase 2 note: migrate to a JDBC job store backed by Cloud SQL (Postgres) so
 * that Quartz timers survive pod restarts without the recovery scan.
 */
@Configuration
public class QuartzConfig {

    /**
     * Defines the {@link AuditIntegrityJob} as a durable Quartz job
     * (survives scheduler restart with in-memory store).
     */
    @Bean
    public JobDetail auditIntegrityJobDetail() {
        return JobBuilder.newJob(AuditIntegrityJob.class)
                .withIdentity("auditIntegrityJob", "audit")
                .withDescription("Daily SHA-256 hash-chain verification of the audit log (P1-20)")
                .storeDurably()
                .build();
    }

    /**
     * Triggers {@code auditIntegrityJob} every day at 02:00 local time.
     *
     * <p>Cron expression {@code "0 0 2 * * ?"} means:
     * second=0, minute=0, hour=2, every day-of-month, every month, any day-of-week.
     *
     * <p>Note: Quartz cron runs in the JVM's default timezone.  The Cloud Run
     * container is started with {@code TZ=America/Toronto} (set in the Dockerfile
     * or as a Cloud Run environment variable) so that this fires at 2 AM Ontario time.
     */
    @Bean
    public Trigger auditIntegrityTrigger(JobDetail auditIntegrityJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(auditIntegrityJobDetail)
                .withIdentity("auditIntegrityTrigger", "audit")
                .withDescription("Fire auditIntegrityJob at 02:00 daily")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 2 * * ?"))
                .build();
    }

    // ── P2-06: Daily analytics pipeline ──────────────────────────────────────

    /**
     * Defines the {@link AnalyticsJob} as a durable Quartz job.
     */
    @Bean
    public JobDetail analyticsJobDetail() {
        return JobBuilder.newJob(AnalyticsJob.class)
                .withIdentity("analyticsJob", "analytics")
                .withDescription("Daily analytics aggregation pipeline (P2-06)")
                .storeDurably()
                .build();
    }

    /**
     * Triggers {@code analyticsJob} every day at 03:00 local time.
     *
     * <p>Scheduled one hour after {@code auditIntegrityJob} (02:00) to avoid
     * resource contention during the nightly maintenance window.
     *
     * <p>Cron expression {@code "0 0 3 * * ?"} means:
     * second=0, minute=0, hour=3, every day-of-month, every month, any day-of-week.
     */
    @Bean
    public Trigger analyticsTrigger(JobDetail analyticsJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(analyticsJobDetail)
                .withIdentity("analyticsTrigger", "analytics")
                .withDescription("Fire analyticsJob at 03:00 daily")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 3 * * ?"))
                .build();
    }

    // ── P3-03: Daily insurance renewal check ─────────────────────────────────

    /**
     * Defines the {@link InsuranceRenewalJob} as a durable Quartz job.
     */
    @Bean
    public JobDetail insuranceRenewalJobDetail() {
        return JobBuilder.newJob(InsuranceRenewalJob.class)
                .withIdentity("insuranceRenewalJob", "insurance")
                .withDescription("Daily insurance expiry check — send reminders and deactivate expired Workers (P3-03)")
                .storeDurably()
                .build();
    }

    /**
     * Triggers {@code insuranceRenewalJob} every day at 04:00 local time.
     *
     * <p>Scheduled one hour after {@code analyticsJob} (03:00) to spread the
     * nightly maintenance window.
     *
     * <p>Cron expression {@code "0 0 4 * * ?"} means:
     * second=0, minute=0, hour=4, every day-of-month, every month, any day-of-week.
     */
    @Bean
    public Trigger insuranceRenewalTrigger(JobDetail insuranceRenewalJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(insuranceRenewalJobDetail)
                .withIdentity("insuranceRenewalTrigger", "insurance")
                .withDescription("Fire insuranceRenewalJob at 04:00 daily")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 4 * * ?"))
                .build();
    }

    // ── v1.1: Negotiated-marketplace maintenance jobs ─────────────────────────

    /**
     * Defines the {@link PostedJobExpiryJob} as a durable Quartz job.
     * Cancels POSTED or NEGOTIATING jobs that have received no agreement within 24 hours.
     */
    @Bean
    public JobDetail postedJobExpiryJobDetail() {
        return JobBuilder.newJob(PostedJobExpiryJob.class)
                .withIdentity("postedJobExpiryJob", "marketplace")
                .withDescription("Cancel POSTED/NEGOTIATING jobs with no agreement after 24 hours")
                .storeDurably()
                .build();
    }

    /**
     * Triggers {@code postedJobExpiryJob} every hour.
     *
     * <p>Cron expression {@code "0 0 * * * ?"} means: top of every hour.
     * An hourly sweep is sufficient since the 24-hour window gives a 1-hour tolerance.
     */
    @Bean
    public Trigger postedJobExpiryTrigger(JobDetail postedJobExpiryJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(postedJobExpiryJobDetail)
                .withIdentity("postedJobExpiryTrigger", "marketplace")
                .withDescription("Fire postedJobExpiryJob every hour")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?"))
                .build();
    }

    /**
     * Defines the {@link RejectionCountCleanupJob} as a durable Quartz job.
     * Resets {@code worker.jobRejectionCount90d} for workers whose last rejection
     * was more than 90 days ago.
     */
    @Bean
    public JobDetail rejectionCountCleanupJobDetail() {
        return JobBuilder.newJob(RejectionCountCleanupJob.class)
                .withIdentity("rejectionCountCleanupJob", "marketplace")
                .withDescription("Reset worker jobRejectionCount90d after 90-day window expires")
                .storeDurably()
                .build();
    }

    /**
     * Triggers {@code rejectionCountCleanupJob} every day at 05:00 local time.
     *
     * <p>Cron expression {@code "0 0 5 * * ?"} means:
     * second=0, minute=0, hour=5, every day-of-month, every month, any day-of-week.
     */
    @Bean
    public Trigger rejectionCountCleanupTrigger(JobDetail rejectionCountCleanupJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(rejectionCountCleanupJobDetail)
                .withIdentity("rejectionCountCleanupTrigger", "marketplace")
                .withDescription("Fire rejectionCountCleanupJob at 05:00 daily")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 5 * * ?"))
                .build();
    }
}
