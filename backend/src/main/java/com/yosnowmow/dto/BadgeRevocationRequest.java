package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/admin/workers/{uid}/badges/{badgeType}/revoke}.
 *
 * <p>The Admin must provide a reason for the revocation, which is written to
 * the audit log and stored on the badge document.
 */
public class BadgeRevocationRequest {

    /** Mandatory reason text for the badge revocation (written to audit log). */
    @NotBlank(message = "reason is required for badge revocation audit")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
