package com.yosnowmow.model;

import com.google.cloud.Timestamp;

/**
 * A single entry in the {@link JobOffer#getMessages()} negotiation thread.
 *
 * Each move by either party (initial offer, counter, photo request, accept, reject)
 * appends one OfferMessage to the thread.  Entries are never updated in-place.
 */
public class OfferMessage {

    /**
     * Who made this move.
     * "worker" | "requester"
     */
    private String actor;

    /**
     * The action taken.
     * Worker actions:  "accept" | "counter" | "photo_request" | "withdraw"
     * Requester actions: "accept" | "counter" | "reject"
     */
    private String action;

    /** Price in cents proposed in this move (null for photo_request, withdraw, or plain accept). */
    private Integer priceCents;

    /** Optional free-text note accompanying this move. */
    private String note;

    private Timestamp createdAt;

    /** Required by Firestore deserialisation. */
    public OfferMessage() {}

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
