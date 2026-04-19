package com.yosnowmow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for Admin dispute resolution.
 *
 * Used by: POST /api/disputes/{disputeId}/resolve (ADMIN only)
 */
public class ResolveDisputeRequest {

    /**
     * Admin's resolution decision.
     * Must be one of: RELEASED | REFUNDED | SPLIT
     */
    @NotBlank(message = "resolution is required")
    private String resolution;

    /**
     * Percentage of the Worker's standard payout to transfer.
     * Required when resolution = SPLIT; ignored otherwise.
     * Range: 0–100.
     */
    @Min(value = 0, message = "splitPercentageToWorker must be 0–100")
    @Max(value = 100, message = "splitPercentageToWorker must be 0–100")
    private int splitPercentageToWorker;

    /** Admin's notes explaining the resolution decision (optional). */
    private String adminNotes;

    /** Required by Jackson deserialisation. */
    public ResolveDisputeRequest() {}

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public int getSplitPercentageToWorker() { return splitPercentageToWorker; }
    public void setSplitPercentageToWorker(int splitPercentageToWorker) {
        this.splitPercentageToWorker = splitPercentageToWorker;
    }

    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String adminNotes) { this.adminNotes = adminNotes; }
}
