package com.yosnowmow.dto;

/**
 * Request body for {@code POST /api/job-requests/{requestId}/respond}.
 *
 * Sent by a Worker to accept or decline a job offer.
 */
public class RespondToJobRequestDto {

    /** {@code true} = accept the job; {@code false} = decline. */
    private boolean accepted;

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
}
