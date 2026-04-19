package com.yosnowmow.scheduler;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

/**
 * Quartz job that resets {@code worker.jobRejectionCount90d} for Workers whose
 * rejection counter has not been incremented in more than 90 days.
 *
 * Fires at 05:00 daily (see {@link com.yosnowmow.config.QuartzConfig}).
 *
 * <h3>Approach</h3>
 * The counter is stored as a simple integer that increments each time a Requester
 * rejects a Worker from a job.  To support the 90-day rolling window we also store
 * {@code worker.lastRejectedAt} (set by OfferService).  This job zeroes the counter
 * when {@code lastRejectedAt} is older than 90 days (or absent).
 *
 * <h3>Admin thresholds</h3>
 * 3 = informational flag, 5 = warning flag, 10 = critical — admin review required.
 * Flags are evaluated by the Admin dashboard query, not this job.
 *
 * Spec ref: §16.9
 */
@DisallowConcurrentExecution
public class RejectionCountCleanupJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(RejectionCountCleanupJob.class);

    private static final String USERS_COLLECTION = "users";
    private static final long   WINDOW_SECONDS   = 90L * 24 * 3600;

    @Autowired
    private Firestore firestore;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("RejectionCountCleanupJob running");
        try {
            Instant cutoff   = Instant.now().minusSeconds(WINDOW_SECONDS);
            Timestamp cutoffTs = Timestamp.ofTimeSecondsAndNanos(cutoff.getEpochSecond(), 0);

            // Find workers whose jobRejectionCount90d > 0 and lastRejectedAt is older than 90 days.
            QuerySnapshot snap = firestore.collection(USERS_COLLECTION)
                    .whereGreaterThan("worker.jobRejectionCount90d", 0)
                    .whereLessThan("worker.lastRejectedAt", cutoffTs)
                    .get().get();

            int reset = 0;
            for (QueryDocumentSnapshot doc : snap.getDocuments()) {
                try {
                    firestore.collection(USERS_COLLECTION).document(doc.getId()).update(
                            "worker.jobRejectionCount90d", 0
                    ).get();
                    reset++;
                } catch (Exception e) {
                    log.error("Failed to reset rejection count for user {}: {}", doc.getId(),
                            e.getMessage(), e);
                }
            }

            log.info("RejectionCountCleanupJob complete — reset {} worker(s)", reset);

        } catch (Exception e) {
            log.error("RejectionCountCleanupJob failed: {}", e.getMessage(), e);
            // Do not rethrow — Quartz should not retry.
        }
    }
}
