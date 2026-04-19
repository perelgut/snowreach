package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for {@code POST /api/admin/workers/{uid}/background-check-decision}.
 *
 * <p>The Admin must specify a decision of {@code "APPROVED"} or {@code "REJECTED"}
 * along with a mandatory reason for the audit log.
 */
public class BackgroundCheckDecisionRequest {

    /**
     * Admin's decision on the background check review.
     * Must be {@code "APPROVED"} or {@code "REJECTED"}.
     */
    @NotBlank(message = "decision is required")
    @Pattern(regexp = "APPROVED|REJECTED",
             message = "decision must be 'APPROVED' or 'REJECTED'")
    private String decision;

    /** Reason for the decision — written to the audit log. */
    @NotBlank(message = "reason is required for audit purposes")
    private String reason;

    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
