package com.yosnowmow.controller;

import com.yosnowmow.dto.CannotCompleteRequest;
import com.yosnowmow.dto.CreateJobRequest;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.MatchingService;
import com.yosnowmow.service.PaymentService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for job lifecycle management.
 *
 * Base path: {@code /api/jobs}
 *
 * <h3>Endpoints (P1-08)</h3>
 * <ul>
 *   <li>POST /api/jobs              — post a new job (REQUESTER)</li>
 *   <li>GET  /api/jobs              — list jobs for the caller; admin can filter all</li>
 *   <li>GET  /api/jobs/{jobId}      — get a single job</li>
 * </ul>
 *
 * <h3>Endpoints (P1-13 / P1-14)</h3>
 * <ul>
 *   <li>POST /api/jobs/{jobId}/start            — Worker starts the job (IN_PROGRESS)</li>
 *   <li>POST /api/jobs/{jobId}/complete         — Worker marks the job complete</li>
 *   <li>POST /api/jobs/{jobId}/cannot-complete  — Worker reports they cannot finish</li>
 *   <li>POST /api/jobs/{jobId}/dispute          — Requester opens a dispute</li>
 *   <li>POST /api/jobs/{jobId}/release          — Admin releases payment (RELEASED)</li>
 *   <li>POST /api/jobs/{jobId}/cancel           — Requester or Admin cancels the job (P1-14)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;
    private final MatchingService matchingService;
    private final PaymentService paymentService;

    public JobController(JobService jobService,
                         MatchingService matchingService,
                         PaymentService paymentService) {
        this.jobService = jobService;
        this.matchingService = matchingService;
        this.paymentService = paymentService;
    }

    // ── P1-08: Create and read ────────────────────────────────────────────────

    /**
     * Posts a new job (REQUESTED state).
     *
     * Kicks off Worker matching asynchronously after the response is returned —
     * the Requester does not wait for matching to complete.
     */
    @PostMapping
    @RequiresRole("requester")
    public ResponseEntity<Job> postJob(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateJobRequest req) {

        Job created = jobService.createJob(caller, req);
        matchingService.matchAndStoreWorkers(created.getJobId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns a single job document.
     *
     * Per spec §5.3, {@code propertyAddress} is hidden from Workers until the
     * job reaches CONFIRMED.
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<Job> getJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Job job = jobService.getJobForCaller(jobId, caller);

        if (caller.hasRole("worker")
                && !caller.hasRole("admin")
                && !isConfirmedOrLater(job.getStatus())) {
            job.setPropertyAddress(null);
            job.setPropertyCoords(null);
        }

        return ResponseEntity.ok(job);
    }

    /**
     * Lists jobs.  Regular users see only their own; admins may filter by status,
     * requesterId, or workerId.
     */
    @GetMapping
    public ResponseEntity<List<Job>> listJobs(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String requesterId,
            @RequestParam(required = false) String workerId,
            @RequestParam(defaultValue = "20") int limit) {

        List<Job> jobs = caller.hasRole("admin")
                ? jobService.listJobs(status, requesterId, workerId, limit)
                : jobService.listJobsForUser(caller.uid());

        return ResponseEntity.ok(jobs);
    }

    // ── P1-13: State-machine transitions ─────────────────────────────────────

    /**
     * Worker starts the job — transitions CONFIRMED → IN_PROGRESS.
     *
     * Only the assigned Worker may call this endpoint.
     * Permission is enforced inside {@code JobService.transition()}.
     */
    @PostMapping("/{jobId}/start")
    @RequiresRole("worker")
    public ResponseEntity<Job> startJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Job job = jobService.transition(jobId, "IN_PROGRESS", caller.uid(),
                caller.hasRole("admin"));
        return ResponseEntity.ok(job);
    }

    /**
     * Worker marks the job complete — transitions IN_PROGRESS → COMPLETE.
     *
     * Only the assigned Worker may call this endpoint.
     * Scheduling the 4-hour auto-release Quartz timer is a side effect inside
     * {@code JobService.transition()}.
     */
    @PostMapping("/{jobId}/complete")
    @RequiresRole("worker")
    public ResponseEntity<Job> completeJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Job job = jobService.transition(jobId, "COMPLETE", caller.uid(),
                caller.hasRole("admin"));
        return ResponseEntity.ok(job);
    }

    /**
     * Worker reports they cannot complete the job — transitions IN_PROGRESS → INCOMPLETE.
     *
     * The reason and optional note are stored on the job document.
     * A Worker or Admin may call this endpoint.
     */
    @PostMapping("/{jobId}/cannot-complete")
    @RequiresRole("worker")
    public ResponseEntity<Job> cannotComplete(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CannotCompleteRequest req) {

        // Persist the reason and note before transitioning.
        Map<String, Object> extras = new HashMap<>();
        extras.put("cannotCompleteReason", req.getReason());
        extras.put("cannotCompleteNote",   req.getNote());
        extras.put("cannotCompleteAt",
                com.google.cloud.Timestamp.now());

        jobService.transitionStatus(jobId, "INCOMPLETE", caller.uid(), extras);
        return ResponseEntity.ok(jobService.getJob(jobId));
    }

    /**
     * Requester opens a dispute — transitions COMPLETE or INCOMPLETE → DISPUTED.
     *
     * For COMPLETE → DISPUTED, the 2-hour dispute window is validated inside
     * {@code JobService.transition()}.
     */
    @PostMapping("/{jobId}/dispute")
    @RequiresRole("requester")
    public ResponseEntity<Job> disputeJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        // Determine current status to route to the correct transition.
        Job current = jobService.getJobForCaller(jobId, caller);
        String targetFrom = current.getStatus(); // COMPLETE or INCOMPLETE

        Job job = jobService.transition(jobId, "DISPUTED", caller.uid(), false);
        return ResponseEntity.ok(job);
    }

    /**
     * Admin releases payment — transitions COMPLETE, INCOMPLETE, or DISPUTED → RELEASED.
     *
     * After the status transition, the controller triggers the Stripe transfer via
     * {@code PaymentService.releasePayment()} so the Worker is paid out.
     */
    @PostMapping("/{jobId}/release")
    @RequiresRole("admin")
    public ResponseEntity<Job> releaseJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Job job = jobService.getJob(jobId);
        jobService.transition(jobId, "RELEASED", caller.uid(), true);
        paymentService.releasePayment(jobId);
        return ResponseEntity.ok(jobService.getJob(jobId));
    }

    // ── P1-14: Cancellation ───────────────────────────────────────────────────

    /**
     * Cancels a job.  The caller must be the Requester who owns the job or an Admin.
     *
     * <p>Cancellation rules (spec §6):
     * <ul>
     *   <li>REQUESTED       → CANCELLED: free (no payment collected yet)</li>
     *   <li>PENDING_DEPOSIT → CANCELLED: Stripe PaymentIntent is cancelled</li>
     *   <li>CONFIRMED       → CANCELLED: $10 CAD + HST fee charged; remainder refunded</li>
     *   <li>IN_PROGRESS or later: not permitted — use the dispute process</li>
     * </ul>
     *
     * @param jobId  Firestore document ID
     * @param caller authenticated caller
     * @return the updated Job (status = CANCELLED)
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<Job> cancelJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        boolean isAdmin = caller.hasRole("admin");

        // Validate + transition (throws InvalidTransitionException / AccessDeniedException).
        String previousStatus = jobService.cancelJob(jobId, caller.uid(), isAdmin);

        // Trigger appropriate Stripe operation based on the previous status.
        switch (previousStatus) {
            case "CONFIRMED"       -> paymentService.cancelWithFee(jobId);
            case "PENDING_DEPOSIT" -> paymentService.cancelPaymentIntent(jobId);
            // REQUESTED: no payment was collected — nothing to do.
        }

        return ResponseEntity.ok(jobService.getJob(jobId));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private boolean isConfirmedOrLater(String status) {
        return switch (status) {
            case "CONFIRMED", "IN_PROGRESS", "COMPLETE", "INCOMPLETE",
                 "DISPUTED", "RELEASED", "REFUNDED", "SETTLED" -> true;
            default -> false;
        };
    }
}
