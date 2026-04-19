package com.yosnowmow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request body for PATCH /api/users/{uid}/worker/capacity (P2-05).
 *
 * <p>Raising {@code maxConcurrentJobs} above 1 requires a minimum rating
 * of 4.0 and at least 10 completed jobs; these prerequisites are validated
 * by {@link com.yosnowmow.service.WorkerService#updateCapacity}.
 */
public class WorkerCapacityRequest {

    /**
     * New maximum number of concurrent jobs for this Worker.
     * Allowed range: 1–3.
     */
    @Min(value = 1, message = "maxConcurrentJobs must be at least 1")
    @Max(value = 3, message = "maxConcurrentJobs must be at most 3")
    private int maxConcurrentJobs;

    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int maxConcurrentJobs) {
        this.maxConcurrentJobs = maxConcurrentJobs;
    }
}
