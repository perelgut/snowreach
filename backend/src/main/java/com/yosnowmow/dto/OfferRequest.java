package com.yosnowmow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for Worker offer submissions and Requester responses.
 *
 * Used by:
 *   POST /api/jobs/{jobId}/offers            — Worker's initial offer
 *   PUT  /api/jobs/{jobId}/offers/{workerId} — Requester's counter or accept
 *
 * {@code action} is the only required field.  {@code priceCents} is required for
 * "counter" actions; optional for "accept" (uses the other party's last price).
 */
public class OfferRequest {

    /**
     * Worker actions:    "accept" | "counter" | "photo_request" | "withdraw"
     * Requester actions: "accept" | "counter" | "reject"
     */
    @NotBlank
    private String action;

    /**
     * Price in cents.  Required when action is "counter".
     * Must be ≥ 100 (= $1.00 CAD) when present.
     */
    @Min(value = 100, message = "Price must be at least $1.00")
    private Integer priceCents;

    /**
     * Optional free-text note.  Max 500 characters.
     * Used for photo request notes, counter-offer explanations, etc.
     */
    @Size(max = 500)
    private String note;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
