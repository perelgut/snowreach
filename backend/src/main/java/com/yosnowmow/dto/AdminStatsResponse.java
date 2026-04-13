package com.yosnowmow.dto;

/**
 * Response body for {@code GET /api/admin/stats}.
 *
 * All monetary values are in CAD (not cents).
 * "Today" is computed from midnight in the America/Toronto timezone.
 */
public class AdminStatsResponse {

    private long jobsToday;
    private long activeJobs;
    private double revenueToday;
    private long openDisputes;
    private long newUsersToday;
    private long totalWorkers;
    private long totalRequesters;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public long getJobsToday()        { return jobsToday; }
    public void setJobsToday(long v)  { this.jobsToday = v; }

    public long getActiveJobs()       { return activeJobs; }
    public void setActiveJobs(long v) { this.activeJobs = v; }

    public double getRevenueToday()        { return revenueToday; }
    public void setRevenueToday(double v)  { this.revenueToday = v; }

    public long getOpenDisputes()       { return openDisputes; }
    public void setOpenDisputes(long v) { this.openDisputes = v; }

    public long getNewUsersToday()       { return newUsersToday; }
    public void setNewUsersToday(long v) { this.newUsersToday = v; }

    public long getTotalWorkers()       { return totalWorkers; }
    public void setTotalWorkers(long v) { this.totalWorkers = v; }

    public long getTotalRequesters()       { return totalRequesters; }
    public void setTotalRequesters(long v) { this.totalRequesters = v; }
}
