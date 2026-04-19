package com.yosnowmow.config;

import com.google.cloud.firestore.Firestore;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Custom Spring Boot Actuator health indicators (P2-08).
 *
 * <p>Both indicators are exposed under {@code GET /api/health}
 * (configured in {@code application.yml} with
 * {@code management.endpoint.health.show-components: always}).
 *
 * <p>The Actuator strips the {@code HealthIndicator} suffix from the bean
 * name to derive the component key in the health response, so:
 * <ul>
 *   <li>{@code firebaseHealthIndicator} → {@code components.firebase}</li>
 *   <li>{@code quartzHealthIndicator}   → {@code components.quartz}</li>
 * </ul>
 *
 * <h3>Cloud Run liveness probe</h3>
 * Cloud Run hits {@code GET /api/health} every 10 seconds.
 * If the endpoint returns HTTP 5xx for 3 consecutive checks, Cloud Run
 * restarts the container.  The indicators here therefore use conservative
 * timeouts so a slow Firestore response does not cascade into a restart loop.
 */
@Configuration
public class HealthConfig {

    private static final Logger log = LoggerFactory.getLogger(HealthConfig.class);

    /**
     * Verifies that the operational Firestore database is reachable by
     * attempting a lightweight document read on the {@code _health/ping}
     * path with a 5-second timeout.
     *
     * <p>{@code _health/ping} need not exist — a "not found" (no-error)
     * response is sufficient proof that the Firestore connection is healthy.
     * Only a network timeout or authentication error is treated as DOWN.
     *
     * @param firestore the operational Firestore instance (not the audit one)
     */
    @Bean("firebaseHealthIndicator")
    public HealthIndicator firebaseHealthIndicator(Firestore firestore) {
        return () -> {
            try {
                firestore.collection("_health").document("ping")
                        .get().get(5, TimeUnit.SECONDS);
                return Health.up()
                        .withDetail("firestore", "reachable")
                        .build();
            } catch (TimeoutException e) {
                log.warn("Firestore health check timed out after 5 s");
                return Health.down()
                        .withDetail("firestore", "timeout after 5 s")
                        .build();
            } catch (Exception e) {
                log.warn("Firestore health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("firestore", "unreachable")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * Verifies that the Quartz Scheduler is running (started and not in standby mode).
     *
     * <p>Returns UP with the scheduler name and thread pool size.
     * Returns DOWN if the scheduler has not been started, is paused in standby,
     * or throws a {@link SchedulerException}.
     *
     * @param scheduler the Spring-managed Quartz {@link Scheduler} bean
     */
    @Bean("quartzHealthIndicator")
    public HealthIndicator quartzHealthIndicator(Scheduler scheduler) {
        return () -> {
            try {
                if (!scheduler.isStarted()) {
                    return Health.down()
                            .withDetail("quartz", "not started")
                            .build();
                }
                if (scheduler.isInStandbyMode()) {
                    return Health.down()
                            .withDetail("quartz", "in standby mode")
                            .build();
                }
                return Health.up()
                        .withDetail("schedulerName", scheduler.getSchedulerName())
                        .withDetail("status", "running")
                        .build();
            } catch (SchedulerException e) {
                log.warn("Quartz health check failed: {}", e.getMessage());
                return Health.down()
                        .withDetail("quartz", "error")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }
}
