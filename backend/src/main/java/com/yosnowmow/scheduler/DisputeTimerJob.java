package com.yosnowmow.scheduler;

import com.yosnowmow.service.JobService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that fires 4 hours after a job reaches COMPLETE state.
 *
 * If the job is still in COMPLETE status (no dispute was filed within the
 * 2-hour window and the Requester did not submit ratings), this job
 * auto-releases the escrow by transitioning the job to RELEASED.
 *
 * If the job is already DISPUTED, RELEASED, REFUNDED, or any other status,
 * the job does nothing — the dispute/rating flow has superseded the timer.
 *
 * Job data key:
 *   {@code jobId} — the YoSnowMow Firestore job document ID
 *
 * Scheduled by {@link JobService#scheduleAutoRelease(String)} immediately after
 * a job transitions to COMPLETE.
 */
@DisallowConcurrentExecution
public class DisputeTimerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DisputeTimerJob.class);

    @Autowired
    private JobService jobService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        String jobId = data.getString("jobId");

        log.info("DisputeTimerJob fired for job {} — checking for auto-release", jobId);

        try {
            var job = jobService.getJob(jobId);

            if (!"COMPLETE".equals(job.getStatus())) {
                log.info("Job {} is {} — auto-release skipped", jobId, job.getStatus());
                return;
            }

            // Auto-release: transition to RELEASED as the system actor.
            jobService.transition(jobId, "RELEASED", "system", false);
            log.info("Job {} auto-released after 4-hour timer", jobId);

        } catch (Exception e) {
            // Do not throw JobExecutionException — we don't want Quartz to retry.
            log.error("DisputeTimerJob failed for job {}: {}", jobId, e.getMessage(), e);
        }
    }
}
