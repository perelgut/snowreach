package com.yosnowmow.scheduler;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.service.AuditLogService;
import com.yosnowmow.service.NotificationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Quartz job that expires POSTED or NEGOTIATING jobs that have not reached
 * agreement within 24 hours of being posted.
 *
 * Fires every hour (see {@link com.yosnowmow.config.QuartzConfig}).
 *
 * For each expired job the job:
 * <ol>
 *   <li>Transitions the job to CANCELLED with {@code cancelledBy = "system_expiry"}</li>
 *   <li>Writes an audit entry</li>
 *   <li>Notifies the Requester</li>
 * </ol>
 *
 * Spec ref: §16.7 (jobs that receive no agreement within 24 hours are auto-cancelled)
 */
@DisallowConcurrentExecution
public class PostedJobExpiryJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(PostedJobExpiryJob.class);

    private static final String JOBS_COLLECTION    = "jobs";
    private static final long   EXPIRY_SECONDS     = 24L * 3600;

    @Autowired
    private Firestore firestore;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private NotificationService notificationService;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("PostedJobExpiryJob running");
        try {
            Instant cutoff = Instant.now().minusSeconds(EXPIRY_SECONDS);
            Timestamp cutoffTs = Timestamp.ofTimeSecondsAndNanos(cutoff.getEpochSecond(), 0);

            // Query for POSTED and NEGOTIATING jobs whose postedAt is older than 24 hours.
            for (String status : List.of("POSTED", "NEGOTIATING")) {
                QuerySnapshot snap = firestore.collection(JOBS_COLLECTION)
                        .whereEqualTo("status", status)
                        .whereLessThan("postedAt", cutoffTs)
                        .get().get();

                for (QueryDocumentSnapshot doc : snap.getDocuments()) {
                    String jobId      = doc.getId();
                    String requesterId = doc.getString("requesterId");
                    log.info("Expiring job {} (status={}, postedAt={})", jobId, status,
                            doc.getTimestamp("postedAt"));

                    try {
                        Timestamp now = Timestamp.now();
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("status",      "CANCELLED");
                        updates.put("cancelledAt", now);
                        updates.put("cancelledBy", "system_expiry");
                        updates.put("updatedAt",   now);

                        auditLogService.write("system", "JOB_EXPIRED_NO_AGREEMENT",
                                "job", jobId, null, updates);

                        firestore.collection(JOBS_COLLECTION).document(jobId)
                                 .update(updates).get();

                        if (requesterId != null) {
                            notificationService.notifyRequesterNoWorkers(requesterId, jobId);
                        }

                    } catch (Exception e) {
                        log.error("Failed to expire job {}: {}", jobId, e.getMessage(), e);
                    }
                }
            }

            log.info("PostedJobExpiryJob complete");

        } catch (Exception e) {
            log.error("PostedJobExpiryJob failed: {}", e.getMessage(), e);
            // Do not rethrow as JobExecutionException — Quartz should not retry.
        }
    }
}
