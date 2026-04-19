package com.yosnowmow.scheduler;

import com.yosnowmow.service.UserService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Quartz job that fires when a user's suspension period expires (P3-06).
 *
 * Scheduled dynamically by {@link com.yosnowmow.controller.AdminController#suspendUser}
 * with {@code delayMs = durationDays * 86_400_000L}.
 *
 * Job data keys:
 * <ul>
 *   <li>{@code uid}      — Firebase Auth UID of the suspended user</li>
 *   <li>{@code adminUid} — always {@code "SYSTEM"} for auto-unsuspends</li>
 * </ul>
 */
@DisallowConcurrentExecution
public class AutoUnsuspendJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AutoUnsuspendJob.class);

    /** Group name used for all auto-unsuspend Quartz jobs. */
    public static final String JOB_GROUP = "auto-unsuspend";

    @Autowired
    private UserService userService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap data = context.getMergedJobDataMap();
        String uid = data.getString("uid");

        log.info("AutoUnsuspendJob fired: uid={}", uid);

        try {
            userService.unbanUser(uid, "SYSTEM", "Suspension period expired — auto-unsuspended");
        } catch (Exception e) {
            log.error("AutoUnsuspendJob failed for uid={}: {}", uid, e.getMessage(), e);
        }
    }
}
