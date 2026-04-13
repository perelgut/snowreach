package com.yosnowmow.model;

import com.google.cloud.Timestamp;

/**
 * Represents a document from the Firestore {@code jobRequests/{jobId}_{workerId}} collection.
 *
 * One document is created per dispatch attempt (one per Worker per job).
 * Written when an offer is sent and updated when the Worker responds or the timer expires.
 *
 * Document ID format: {@code {jobId}_{workerId}}
 */
public class JobRequest {

    /**
     * Composite document ID: "{jobId}_{workerId}".
     * Stored in the document itself for convenience when reading from a collection query.
     */
    private String jobRequestId;

    private String jobId;
    private String workerId;

    /**
     * Current status of this dispatch attempt.
     * Valid values: PENDING | ACCEPTED | DECLINED | EXPIRED
     */
    private String status;

    /** When the offer was sent to the Worker. */
    private Timestamp sentAt;

    /** Offer expiry — sentAt + 10 minutes. */
    private Timestamp expiresAt;

    /** When the Worker responded; null if the offer expired. */
    private Timestamp respondedAt;

    /** Required by Firestore deserialisation. */
    public JobRequest() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getJobRequestId() { return jobRequestId; }
    public void setJobRequestId(String jobRequestId) { this.jobRequestId = jobRequestId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getSentAt() { return sentAt; }
    public void setSentAt(Timestamp sentAt) { this.sentAt = sentAt; }

    public Timestamp getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Timestamp expiresAt) { this.expiresAt = expiresAt; }

    public Timestamp getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Timestamp respondedAt) { this.respondedAt = respondedAt; }
}
