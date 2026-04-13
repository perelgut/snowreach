package com.yosnowmow.controller;

import com.yosnowmow.dto.CreateJobRequest;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.JobService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for job posting and retrieval.
 *
 * P1-08 scope: create and read.  State-transition endpoints (accept, start,
 * complete, cancel, dispute) are added in P1-13 and P1-14.
 *
 * Base path: {@code /api/jobs}
 *
 * Endpoints:
 *   POST /api/jobs          — post a new job (REQUESTER role)
 *   GET  /api/jobs          — list jobs for the caller; admin can filter all
 *   GET  /api/jobs/{jobId}  — get a single job
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    /**
     * Posts a new job.
     *
     * The caller must hold the "requester" role.
     * Returns 201 with the full job document (status = REQUESTED).
     *
     * Note: the property address in the Requester's profile is NOT automatically
     * used — the Requester provides the address to clear in each job request.
     * This allows them to post for a different address (e.g. elderly parent's home).
     */
    @PostMapping
    @RequiresRole("requester")
    public ResponseEntity<Job> postJob(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateJobRequest req) {

        Job created = jobService.createJob(caller, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns a single job document.
     *
     * Access: Requester sees own jobs; Worker sees jobs they are assigned to;
     * Admin sees all.  The controller delegates access checking to
     * {@link JobService#getJobForCaller}.
     *
     * Note: per spec §5.3, {@code propertyAddress} is hidden from Workers until
     * the job reaches CONFIRMED.  That field-level redaction is handled here.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<Job> getJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Job job = jobService.getJobForCaller(jobId, caller);

        // Redact property address from Workers until CONFIRMED
        if (caller.hasRole("worker")
                && !caller.hasRole("admin")
                && !isConfirmedOrLater(job.getStatus())) {
            job.setPropertyAddress(null);
            job.setPropertyCoords(null);
        }

        return ResponseEntity.ok(job);
    }

    /**
     * Lists jobs.
     *
     * - Regular users (Requester or Worker): returns their own jobs only;
     *   filter params are ignored.
     * - Admins: may supply optional filter params (status, requesterId, workerId)
     *   and a limit (default 20, max 100).
     */
    @GetMapping
    public ResponseEntity<List<Job>> listJobs(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requesterId,
            @RequestParam(required = false) String workerId,
            @RequestParam(defaultValue = "20") int limit) {

        List<Job> jobs;

        if (caller.hasRole("admin")) {
            jobs = jobService.listJobs(status, requesterId, workerId, limit);
        } else {
            jobs = jobService.listJobsForUser(caller.uid());
        }

        return ResponseEntity.ok(jobs);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns true if the job has reached CONFIRMED or any later state —
     * the point at which the property address is no longer private.
     */
    private boolean isConfirmedOrLater(String status) {
        return switch (status) {
            case "CONFIRMED", "IN_PROGRESS", "COMPLETE", "INCOMPLETE",
                 "DISPUTED", "RELEASED", "REFUNDED", "SETTLED" -> true;
            default -> false;
        };
    }
}
