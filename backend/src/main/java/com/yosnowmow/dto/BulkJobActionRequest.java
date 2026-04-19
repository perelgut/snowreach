package com.yosnowmow.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * Request body for POST /api/admin/jobs/bulk-action (P3-06).
 *
 * Applies a single action ("release" or "refund") to a list of job IDs.
 */
public class BulkJobActionRequest {

    /** One or more job document IDs to act on. */
    @NotEmpty(message = "jobIds must not be empty")
    private List<String> jobIds;

    /** Action to apply — must be "release" or "refund". */
    @NotNull(message = "action is required")
    @Pattern(regexp = "^(release|refund)$", message = "action must be 'release' or 'refund'")
    private String action;

    public BulkJobActionRequest() {}

    public List<String> getJobIds() { return jobIds; }
    public void setJobIds(List<String> jobIds) { this.jobIds = jobIds; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
}
