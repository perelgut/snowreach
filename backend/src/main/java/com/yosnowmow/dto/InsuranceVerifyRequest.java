package com.yosnowmow.dto;

/**
 * Request body for {@code POST /api/admin/workers/{uid}/insurance-verify}.
 *
 * <p>The Admin must specify whether the uploaded insurance document is accepted.
 * <ul>
 *   <li>{@code approved = true}  → status set to {@code VALID}; Worker notified.</li>
 *   <li>{@code approved = false} → status reset to {@code NONE}; doc URL cleared; Worker notified.</li>
 * </ul>
 */
public class InsuranceVerifyRequest {

    /** True to approve the document; false to reject it. */
    private boolean approved;

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
}
