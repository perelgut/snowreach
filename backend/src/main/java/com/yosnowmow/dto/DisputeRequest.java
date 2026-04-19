package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for opening a dispute or submitting / updating a statement.
 *
 * Used by:
 *   POST /api/jobs/{jobId}/dispute         — Requester opens a dispute
 *   POST /api/disputes/{disputeId}/statement — either party submits a statement
 */
public class DisputeRequest {

    /**
     * The caller's account of what happened.
     * Required and must not be blank.
     */
    @NotBlank(message = "statement is required")
    @Size(max = 2000, message = "statement must be 2000 characters or fewer")
    private String statement;

    /** Required by Jackson deserialisation. */
    public DisputeRequest() {}

    public String getStatement() { return statement; }
    public void setStatement(String statement) { this.statement = statement; }
}
