package com.yosnowmow.dto;

/**
 * Narrow public view of a Worker's profile — safe to return to any job participant.
 *
 * Contains only the three fields the Requester UI needs when displaying Worker
 * offers in the negotiation panel (JobStatus.jsx).  Private fields (address,
 * pricing, financials, background check details) are never included.
 *
 * Returned by GET /api/users/{uid}/worker/public.
 */
public class WorkerPublicProfileResponse {

    /** Worker's display name (sourced from User.name). */
    private final String displayName;

    /** Average star rating (null until sufficient completed jobs). */
    private final Double averageRating;

    /** Total number of completed jobs. */
    private final int totalJobsCompleted;

    public WorkerPublicProfileResponse(String displayName, Double averageRating, int totalJobsCompleted) {
        this.displayName       = displayName;
        this.averageRating     = averageRating;
        this.totalJobsCompleted = totalJobsCompleted;
    }

    public String  getDisplayName()       { return displayName; }
    public Double  getAverageRating()     { return averageRating; }
    public int     getTotalJobsCompleted() { return totalJobsCompleted; }
}
