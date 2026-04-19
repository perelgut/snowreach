package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/admin/users/{uid}/ban and
 * POST /api/admin/users/{uid}/unban (P3-06).
 */
public class BanUserRequest {

    /** Human-readable reason for the action, written to the audit log. */
    @NotBlank(message = "reason is required")
    private String reason;

    public BanUserRequest() {}

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
