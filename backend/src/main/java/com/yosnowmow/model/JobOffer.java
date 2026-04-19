package com.yosnowmow.model;

import com.google.cloud.Timestamp;
import java.util.List;

/**
 * Represents a document from the Firestore {@code jobOffers/{jobId}_{workerId}} collection.
 *
 * One document exists per (job, worker) pair.  The document captures the full
 * negotiation thread between the Requester and one Worker.
 *
 * Status lifecycle for a single offer:
 *   OPEN → ACCEPTED (Worker accepts the posted or requester-countered price)
 *   OPEN → COUNTERED (Worker counters; or Requester counters back)
 *   OPEN → PHOTO_REQUESTED (Worker needs a property photo before committing)
 *   OPEN → REJECTED (Requester blocks this Worker from the job)
 *   OPEN → WITHDRAWN (Worker withdraws)
 *   ACCEPTED → (job moves to AGREED / ESCROW_HELD — offer document stays as history)
 *
 * Spec ref: §16.4
 */
public class JobOffer {

    /** Composite key: "{jobId}_{workerId}". */
    private String offerId;

    private String jobId;
    private String workerId;

    /**
     * Current state of this offer.
     * Values: "OPEN" | "ACCEPTED" | "COUNTERED" | "PHOTO_REQUESTED" | "REJECTED" | "WITHDRAWN"
     */
    private String status;

    /**
     * The price (in cents) the Worker most recently proposed (initial accept or counter).
     * Null if the Worker has only requested a photo without stating a price.
     */
    private Integer workerPriceCents;

    /**
     * The price (in cents) the Requester most recently counter-proposed.
     * Null until the Requester makes a counter.
     */
    private Integer requesterPriceCents;

    /**
     * Who made the last move in the negotiation.
     * "worker" | "requester"
     */
    private String lastMoveBy;

    /**
     * Optional message from the Worker when submitting their first offer
     * or counter-offer.  Max 500 characters.
     */
    private String workerNote;

    /**
     * Set when the Worker's action is "photo_request".
     * Describes what photo the Worker needs to see before committing.
     */
    private String photoRequestNote;

    /**
     * Full negotiation thread — one entry per move.
     * Appended immutably; never updated in-place.
     */
    private List<OfferMessage> messages;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** Required by Firestore deserialisation. */
    public JobOffer() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getWorkerId() { return workerId; }
    public void setWorkerId(String workerId) { this.workerId = workerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getWorkerPriceCents() { return workerPriceCents; }
    public void setWorkerPriceCents(Integer workerPriceCents) { this.workerPriceCents = workerPriceCents; }

    public Integer getRequesterPriceCents() { return requesterPriceCents; }
    public void setRequesterPriceCents(Integer requesterPriceCents) { this.requesterPriceCents = requesterPriceCents; }

    public String getLastMoveBy() { return lastMoveBy; }
    public void setLastMoveBy(String lastMoveBy) { this.lastMoveBy = lastMoveBy; }

    public String getWorkerNote() { return workerNote; }
    public void setWorkerNote(String workerNote) { this.workerNote = workerNote; }

    public String getPhotoRequestNote() { return photoRequestNote; }
    public void setPhotoRequestNote(String photoRequestNote) { this.photoRequestNote = photoRequestNote; }

    public List<OfferMessage> getMessages() { return messages; }
    public void setMessages(List<OfferMessage> messages) { this.messages = messages; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}
