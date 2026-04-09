package com.yosnowmow.exception;

/**
 * Thrown when a requested job does not exist in Firestore.
 * Maps to HTTP 404 in GlobalExceptionHandler.
 */
public class JobNotFoundException extends RuntimeException {

    private final String jobId;

    public JobNotFoundException(String jobId) {
        super("Job not found: " + jobId);
        this.jobId = jobId;
    }

    public String getJobId() {
        return jobId;
    }
}
