package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/jobs/{jobId}/cannot-complete}.
 *
 * Sent by a Worker when they cannot finish a job they have started.
 */
public class CannotCompleteRequest {

    /**
     * Reason code for why the job cannot be completed.
     * Must be one of the allowed values (spec §4.x).
     */
    @NotBlank(message = "reason is required")
    @Pattern(
        regexp = "equipment_failure|safety_concern|access_blocked|weather|other",
        message = "reason must be one of: equipment_failure, safety_concern, access_blocked, weather, other"
    )
    private String reason;

    /** Optional free-text note (max 500 characters). */
    private String note;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
