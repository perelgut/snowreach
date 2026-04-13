package com.yosnowmow.config;

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
 * No manual wiring is needed here.
 *
 * Startup recovery of in-flight dispatch timers is handled by
 * {@link com.yosnowmow.service.DispatchService#recoverPendingDispatches()},
 * which listens for {@link org.springframework.context.event.ContextRefreshedEvent}.
 *
 * Phase 2 note: migrate to a JDBC job store backed by Cloud SQL (Postgres) so
 * that Quartz timers survive pod restarts without the recovery scan.
 */
@Configuration
public class QuartzConfig {
    // All Quartz configuration is driven by YAML properties and Spring Boot
    // auto-configuration.  No beans need to be declared here for Phase 1.
}
