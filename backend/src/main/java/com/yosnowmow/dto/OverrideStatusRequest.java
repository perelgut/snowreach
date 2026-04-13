package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PATCH /api/admin/jobs/{jobId}/status}.
 *
 * Admin-only: bypasses normal state-machine actor validation
 * but still validates that the transition is in the allowed table.
 */
public class OverrideStatusRequest {

    @NotBlank(message = "targetStatus must not be blank")
    private String targetStatus;

    /** Required audit reason for the override. */
    @NotBlank(message = "reason must not be blank")
    private String reason;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getTargetStatus()             { return targetStatus; }
    public void setTargetStatus(String v)       { this.targetStatus = v; }

    public String getReason()                   { return reason; }
    public void setReason(String v)             { this.reason = v; }
}
