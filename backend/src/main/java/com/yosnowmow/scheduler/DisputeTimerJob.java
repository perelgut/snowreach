package com.yosnowmow.scheduler;

import com.yosnowmow.service.JobService;
import com.yosnowmow.service.PaymentService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that fires after the approval window expires following a job reaching PENDING_APPROVAL.
 *
 * If the job is still in PENDING_APPROVAL status (Requester has not approved or disputed),
 * this job auto-releases the escrow by transitioning the job to RELEASED.
 *
 * If the job is already DISPUTED, RELEASED, REFUNDED, or any other status,
 * the job does nothing — the approve/dispute flow has superseded the timer.
 *
 * Job data key:
 *   {@code jobId} — the YoSnowMow Firestore job document ID
 *
 * Scheduled by {@link JobService#scheduleAutoRelease(String)} immediately after
 * a job transitions to PENDING_APPROVAL.
 */
@DisallowConcurrentExecution
public class DisputeTimerJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DisputeTimerJob.class);

    @Autowired
    private JobService jobService;

    @Autowired
    private PaymentService paymentService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        String jobId = data.getString("jobId");

        log.info("DisputeTimerJob fired for job {} — checking for auto-release", jobId);

        try {
            var job = jobService.getJob(jobId);

            if (!"PENDING_APPROVAL".equals(job.getStatus())) {
                log.info("Job {} is {} — auto-release skipped", jobId, job.getStatus());
                return;
            }

            // Auto-release: Requester did not act within the approval window.
            jobService.transition(jobId, "RELEASED", "system", false);
            paymentService.releasePayment(jobId);
            log.info("Job {} auto-released after approval window expired", jobId);

        } catch (Exception e) {
            // Do not throw JobExecutionException — we don't want Quartz to retry.
            log.error("DisputeTimerJob failed for job {}: {}", jobId, e.getMessage(), e);
        }
    }
}
