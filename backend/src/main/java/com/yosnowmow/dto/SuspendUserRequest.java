package com.yosnowmow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/admin/users/{uid}/suspend (P3-06).
 */
public class SuspendUserRequest {

    /** Human-readable reason for the suspension, written to the audit log. */
    @NotBlank(message = "reason is required")
    private String reason;

    /** Number of days the suspension should last (1–365). */
    @Min(value = 1, message = "durationDays must be at least 1")
    @Max(value = 365, message = "durationDays may not exceed 365")
    private int durationDays;

    public SuspendUserRequest() {}

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }
}
