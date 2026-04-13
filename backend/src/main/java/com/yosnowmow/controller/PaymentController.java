package com.yosnowmow.controller;

import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * REST controller for Stripe payment operations.
 *
 * Endpoints:
 *   POST /api/jobs/{jobId}/payment-intent       — Requester creates escrow PaymentIntent
 *   POST /api/workers/{uid}/connect-onboard     — Worker starts Stripe Connect Express onboarding
 *   POST /api/jobs/{jobId}/release-payment      — Admin releases payout to Worker
 *   POST /api/jobs/{jobId}/refund               — Admin issues full refund to Requester
 *
 * Note: all endpoints are scattered across path patterns rather than under a single
 * {@code /api/payments} prefix so they align naturally with the resources they modify.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final JobService jobService;

    public PaymentController(PaymentService paymentService, JobService jobService) {
        this.paymentService = paymentService;
        this.jobService = jobService;
    }

    // ── P1-11 — Escrow payment intent ─────────────────────────────────────────

    /**
     * Creates (or retrieves an existing) Stripe PaymentIntent for the escrow deposit
     * on a job that is in PENDING_DEPOSIT state.
     *
     * The caller must be the Requester who owns the job.
     *
     * @param jobId  Firestore document ID of the job
     * @param caller authenticated caller
     * @return {@code { "clientSecret": "pi_xxx_secret_yyy" }} for use with Stripe.js
     *         {@code confirmCardPayment}
     */
    @PostMapping("/api/jobs/{jobId}/payment-intent")
    @RequiresRole("requester")
    public ResponseEntity<Map<String, String>> createPaymentIntent(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        // Verify the caller owns this job.
        Job job = jobService.getJob(jobId);
        if (!job.getRequesterId().equals(caller.uid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not own this job");
        }

        String clientSecret = paymentService.createEscrowIntent(jobId);
        return ResponseEntity.ok(Map.of("clientSecret", clientSecret));
    }

    // ── P1-12 — Stripe Connect onboarding ─────────────────────────────────────

    /**
     * Creates or refreshes a Stripe Connect Express onboarding link for a Worker.
     *
     * The caller must be the Worker themselves or an Admin.
     *
     * Query parameters:
     *   {@code returnUrl}  — URL to redirect to after successful onboarding
     *   {@code refreshUrl} — URL to redirect to if the onboarding link expires
     *
     * @param workerUid  Firebase UID of the Worker
     * @param caller     authenticated caller
     * @param returnUrl  post-onboarding redirect URL
     * @param refreshUrl onboarding-link refresh URL
     * @return {@code { "onboardingUrl": "https://connect.stripe.com/..." }}
     */
    @PostMapping("/api/workers/{workerUid}/connect-onboard")
    public ResponseEntity<Map<String, String>> connectOnboard(
            @PathVariable String workerUid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam String returnUrl,
            @RequestParam String refreshUrl) {

        // Only the Worker themselves or an admin may initiate onboarding.
        if (!caller.uid().equals(workerUid) && !caller.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You may only initiate onboarding for your own account");
        }

        String url = paymentService.createConnectOnboardingLink(workerUid, returnUrl, refreshUrl);
        return ResponseEntity.ok(Map.of("onboardingUrl", url));
    }

    // ── P1-12 — Payment release ───────────────────────────────────────────────

    /**
     * Admin endpoint: releases the Worker payout for a completed job.
     *
     * Creates a Stripe Transfer from the platform balance to the Worker's
     * Connected account and transitions the job to RELEASED.
     *
     * @param jobId  Firestore document ID of the job
     * @param caller authenticated admin caller
     */
    @PostMapping("/api/jobs/{jobId}/release-payment")
    @RequiresRole("admin")
    public ResponseEntity<Void> releasePayment(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        paymentService.releasePayment(jobId);
        return ResponseEntity.ok().build();
    }

    // ── P1-12 — Refund ────────────────────────────────────────────────────────

    /**
     * Admin endpoint: issues a full refund to the Requester for a cancelled or
     * disputed job.
     *
     * Cancels the Stripe PaymentIntent if still capturable; otherwise creates a
     * Stripe Refund.  Transitions the job to REFUNDED.
     *
     * @param jobId  Firestore document ID of the job
     * @param caller authenticated admin caller
     */
    @PostMapping("/api/jobs/{jobId}/refund")
    @RequiresRole("admin")
    public ResponseEntity<Void> refundJob(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        paymentService.refundJob(jobId);
        return ResponseEntity.ok().build();
    }
}
