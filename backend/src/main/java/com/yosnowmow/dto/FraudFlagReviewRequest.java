package com.yosnowmow.dto;

/**
 * Request body for admin fraud flag review decisions (P3-05).
 *
 * Both approve and reject endpoints accept the same body structure:
 * an optional {@code notes} field that is recorded on the flag document.
 *
 * <pre>
 * POST /api/admin/fraud-flags/{flagId}/approve
 * POST /api/admin/fraud-flags/{flagId}/reject
 * Body: { "notes": "Investigated — legitimate job" }   // optional
 * </pre>
 */
public class FraudFlagReviewRequest {

    /** Optional admin review notes recorded on the fraud flag document. */
    private String notes;

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
