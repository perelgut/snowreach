package com.yosnowmow.config;

import com.yosnowmow.scheduler.AuditIntegrityJob;
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
 * </ul>
 *
 * Dispatch-offer timers ({@link com.yosnowmow.scheduler.DispatchJob}) and
 * auto-release timers ({@link com.yosnowmow.scheduler.DisputeTimerJob}) are
 * scheduled dynamically by {@code DispatchService} and {@code JobService}
 * rather than as static cron beans.
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
}
