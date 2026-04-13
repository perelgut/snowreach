package com.yosnowmow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Stub notification service — wired in P1-17 (SendGrid email) and P1-18 (Firebase FCM push).
 *
 * All methods are no-ops that log at DEBUG level so dispatch logic in
 * DispatchService can be developed and tested without real notification infrastructure.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Sends a job-offer push notification to a Worker.
     * TODO P1-18: wire to Firebase Cloud Messaging (FCM).
     *
     * @param workerUid Firebase UID of the Worker
     * @param jobId     job document ID
     */
    public void sendJobRequest(String workerUid, String jobId) {
        log.debug("TODO P1-18 — sendJobRequest: worker={} job={}", workerUid, jobId);
    }

    /**
     * Notifies a Requester that no Workers were available for their job.
     * TODO P1-17: wire to SendGrid transactional email.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       job document ID
     */
    public void notifyRequesterNoWorkers(String requesterId, String jobId) {
        log.debug("TODO P1-17 — notifyRequesterNoWorkers: requester={} job={}", requesterId, jobId);
    }

    /**
     * Notifies a Requester that a Worker has accepted their job.
     * TODO P1-18: wire to Firebase Cloud Messaging (FCM) and SendGrid.
     *
     * @param requesterId Firebase UID of the Requester
     * @param jobId       job document ID
     * @param workerId    Firebase UID of the accepting Worker
     */
    public void notifyRequesterJobAccepted(String requesterId, String jobId, String workerId) {
        log.debug("TODO P1-18 — notifyRequesterJobAccepted: requester={} job={} worker={}",
                requesterId, jobId, workerId);
    }
}
