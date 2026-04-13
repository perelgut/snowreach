package com.yosnowmow.scheduler;

import com.yosnowmow.service.DispatchService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that fires when a Worker's 10-minute offer window expires.
 *
 * Spring Boot's {@code spring-boot-starter-quartz} auto-configures a
 * {@code SpringBeanJobFactory} which injects Spring beans into Quartz job
 * instances at construction time, so {@code @Autowired} works without any
 * manual wiring.
 *
 * {@code @DisallowConcurrentExecution} ensures that even if two triggers for
 * the same job key fire simultaneously only one execution proceeds.
 *
 * Job data keys (set by {@link DispatchService#scheduleQuartzTimer}):
 * <ul>
 *   <li>{@code jobId}    — the YoSnowMow job document ID</li>
 *   <li>{@code workerId} — the Worker whose offer has expired</li>
 * </ul>
 */
@DisallowConcurrentExecution
public class DispatchJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DispatchJob.class);

    @Autowired
    private DispatchService dispatchService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data   = context.getMergedJobDataMap();
        String jobId      = data.getString("jobId");
        String workerId   = data.getString("workerId");

        log.debug("DispatchJob fired: jobId={} workerId={}", jobId, workerId);

        try {
            dispatchService.handleOfferExpiry(jobId, workerId);
        } catch (Exception e) {
            // Log but do not throw JobExecutionException — we do not want Quartz to
            // retry the job on failure.  The offer will remain PENDING until the
            // next startup recovery pass or admin intervention.
            log.error("DispatchJob failed for jobId={} workerId={}: {}",
                    jobId, workerId, e.getMessage(), e);
        }
    }
}
