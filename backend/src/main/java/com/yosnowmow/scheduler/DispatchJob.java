package com.yosnowmow.scheduler;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @deprecated Sequential dispatch replaced by OfferService (Phase A).
 *             This job is no longer scheduled; retained to avoid Quartz
 *             deserialization errors if old trigger records exist.
 */
@Deprecated
@DisallowConcurrentExecution
public class DispatchJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(DispatchJob.class);

    @Override
    public void execute(JobExecutionContext context) {
        log.warn("DispatchJob fired but is deprecated — no-op. jobId={} workerId={}",
                context.getMergedJobDataMap().getString("jobId"),
                context.getMergedJobDataMap().getString("workerId"));
    }
}
