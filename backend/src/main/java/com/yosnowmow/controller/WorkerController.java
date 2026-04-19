package com.yosnowmow.controller;

import com.yosnowmow.dto.BackgroundCheckConsentRequest;
import com.yosnowmow.dto.WorkerCapacityRequest;
import com.yosnowmow.dto.WorkerProfileRequest;
import com.yosnowmow.model.User;
import com.yosnowmow.model.WorkerProfile;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.BackgroundCheckService;
import com.yosnowmow.service.BadgeService;
import com.yosnowmow.service.InsuranceService;
import com.yosnowmow.service.WorkerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.concurrent.ExecutionException;

/**
 * REST controller for Worker profile activation and management.
 *
 * Endpoints follow the spec §5 convention of {@code /users/me/worker} for
 * the Worker managing their own profile.  "me" is resolved from the verified
 * Firebase ID token — there is no {userId} path variable on these routes to
 * prevent impersonation.
 *
 * Admin access to update any Worker's profile is at
 * {@code PATCH /api/users/{userId}/worker} (requires ADMIN role).
 *
 * Base path: {@code /api/users}
 *
 * Endpoints:
 *   POST   /api/users/me/worker              — activate Worker role + configure profile
 *   PATCH  /api/users/me/worker              — update own Worker profile
 *   PATCH  /api/users/{userId}/worker        — update any Worker profile (ADMIN only)
 *   PATCH  /api/users/{uid}/worker/capacity  — update concurrent job capacity (own or ADMIN)
 *   GET    /api/users/{uid}/worker/background-check-status — current status (own or ADMIN)
 *   POST   /api/users/{uid}/worker/insurance               — upload insurance PDF (own or ADMIN)
 *   GET    /api/users/{uid}/worker/badges                  — list active trust badges (own or ADMIN)
 */
@RestController
@RequestMapping("/api/users")
public class WorkerController {

    private final WorkerService           workerService;
    private final BackgroundCheckService  backgroundCheckService;
    private final InsuranceService        insuranceService;
    private final BadgeService            badgeService;

    public WorkerController(WorkerService workerService,
                             BackgroundCheckService backgroundCheckService,
                             InsuranceService insuranceService,
                             BadgeService badgeService) {
        this.workerService          = workerService;
        this.backgroundCheckService = backgroundCheckService;
        this.insuranceService       = insuranceService;
        this.badgeService           = badgeService;
    }

    /**
     * Activates the Worker role for the authenticated user and stores their profile.
     *
     * Preconditions:
     *   - User must already have a registered user document (P1-05 POST /api/users).
     *   - No existing Worker profile for this UID (returns 409 if already activated).
     *
     * @param caller the authenticated caller
     * @param req    the Worker profile payload
     * @return HTTP 201 with the full updated User document
     */
    @PostMapping("/me/worker")
    public ResponseEntity<User> activateWorker(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody WorkerProfileRequest req) {

        User updated = workerService.activateWorker(caller, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }

    /**
     * Updates the Worker profile for the authenticated user.
     *
     * Only non-null fields in the request body are applied.
     * Status may be toggled between "available" and "unavailable" here.
     *
     * @param caller the authenticated caller
     * @param req    fields to update
     * @return HTTP 200 with the full updated User document
     */
    @PatchMapping("/me/worker")
    @RequiresRole("worker")
    public ResponseEntity<User> updateOwnWorkerProfile(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody WorkerProfileRequest req) {

        User updated = workerService.updateWorkerProfile(caller.uid(), req);
        return ResponseEntity.ok(updated);
    }

    /**
     * Admin endpoint: update any Worker's profile by UID.
     *
     * Used by admins to adjust status, service area, or other fields without
     * impersonating the Worker.
     *
     * @param userId the Firebase Auth UID of the Worker
     * @param caller the authenticated admin caller
     * @param req    fields to update
     * @return HTTP 200 with the full updated User document
     */
    @PatchMapping("/{userId}/worker")
    @RequiresRole("admin")
    public ResponseEntity<User> updateWorkerProfileAsAdmin(
            @PathVariable String userId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody WorkerProfileRequest req) {

        User updated = workerService.updateWorkerProfile(userId, req);
        return ResponseEntity.ok(updated);
    }

    /**
     * Updates a Worker's maximum concurrent job capacity (P2-05).
     *
     * <p>A Worker may only update their own capacity.  Admins may update
     * any Worker's capacity.  Raising the limit above 1 requires:
     * <ul>
     *   <li>Average rating ≥ 4.0</li>
     *   <li>At least 10 completed jobs</li>
     * </ul>
     *
     * @param uid    Firebase Auth UID of the Worker
     * @param caller the authenticated caller
     * @param req    body containing {@code maxConcurrentJobs} (1–3)
     * @return HTTP 200 with the full updated User document
     */
    @PatchMapping("/{uid}/worker/capacity")
    public ResponseEntity<User> updateWorkerCapacity(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody WorkerCapacityRequest req) {

        User updated = workerService.updateCapacity(
                uid, caller.uid(), caller.hasRole("admin"), req.getMaxConcurrentJobs());
        return ResponseEntity.ok(updated);
    }

    /**
     * Submits a background check to Certn for the given Worker (P3-01).
     *
     * <p>The Worker must explicitly include {@code "consented": true} in the request
     * body.  Only the Worker themselves (matching UID) may initiate their own check;
     * admins may also trigger a check on behalf of any Worker.
     *
     * <p>Returns 409 Conflict if a background check is already in progress or complete.
     *
     * @param uid    Firebase Auth UID of the Worker
     * @param caller the authenticated caller
     * @param req    consent body ({@code consented: true} required)
     * @return HTTP 202 Accepted — the check is submitted asynchronously; result arrives via webhook
     */
    @PostMapping("/{uid}/worker/background-check")
    public ResponseEntity<Void> submitBackgroundCheck(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody BackgroundCheckConsentRequest req)
            throws InterruptedException, ExecutionException {

        // Enforce: only the Worker themselves or an admin may submit the check.
        if (!uid.equals(caller.uid()) && !caller.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Worker themselves or an admin may submit a background check");
        }

        backgroundCheckService.submitBackgroundCheck(uid);

        // 202 Accepted: the check has been submitted to Certn but the result arrives later
        // via the /webhooks/certn callback.
        return ResponseEntity.accepted().build();
    }

    /**
     * Returns the current background check status for a Worker (P3-02).
     *
     * <p>Only the Worker themselves (matching UID) or an Admin may query this endpoint.
     *
     * <p>Response body:
     * <pre>
     * {
     *   "backgroundCheckStatus": "SUBMITTED" | "CLEAR" | "CONSIDER" | "SUSPENDED" | "REJECTED" | null,
     *   "isActive": true | false
     * }
     * </pre>
     *
     * @param uid    Firebase Auth UID of the Worker
     * @param caller the authenticated caller
     * @return HTTP 200 with a status map; 403 if caller is not the Worker or an admin
     */
    @GetMapping("/{uid}/worker/background-check-status")
    public ResponseEntity<Map<String, Object>> getBackgroundCheckStatus(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller)
            throws InterruptedException, ExecutionException {

        // Enforce: only the Worker themselves or an admin may view the status.
        if (!uid.equals(caller.uid()) && !caller.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Worker themselves or an admin may view background check status");
        }

        User user = workerService.getWorkerUser(uid);
        WorkerProfile worker = user.getWorker();

        Map<String, Object> response = new HashMap<>();
        response.put("backgroundCheckStatus",
                worker != null ? worker.getBackgroundCheckStatus() : null);
        response.put("isActive",
                worker != null && worker.isActive());

        return ResponseEntity.ok(response);
    }

    /**
     * Uploads a Worker's insurance declaration PDF (P3-03).
     *
     * <p>Accepts a multipart form with two parts:
     * <ul>
     *   <li>{@code file}       — the insurance certificate PDF (application/pdf, max 20 MB)</li>
     *   <li>{@code expiryDate} — the policy expiry date in ISO-8601 format (YYYY-MM-DD)</li>
     * </ul>
     *
     * <p>Only the Worker themselves or an Admin may upload a document.
     * On success the insurance status is set to {@code PENDING_REVIEW} and the Admin
     * is notified by email.
     *
     * @param uid        Firebase Auth UID of the Worker
     * @param caller     the authenticated caller
     * @param file       the uploaded PDF file
     * @param expiryDate ISO-8601 expiry date string
     * @return HTTP 202 Accepted — Admin review is asynchronous
     */
    @PostMapping("/{uid}/worker/insurance")
    public ResponseEntity<Void> uploadInsuranceDoc(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam("file") MultipartFile file,
            @RequestParam("expiryDate") String expiryDate)
            throws InterruptedException, ExecutionException {

        // Enforce: only the Worker themselves or an admin may upload.
        if (!uid.equals(caller.uid()) && !caller.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Worker themselves or an admin may upload an insurance document");
        }

        // Parse the expiry date — fail fast with a clear 400 rather than a 500.
        LocalDate expiry;
        try {
            expiry = LocalDate.parse(expiryDate);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "expiryDate must be in YYYY-MM-DD format");
        }

        insuranceService.uploadInsuranceDoc(uid, file, expiry);

        // 202 Accepted: document uploaded; Admin review is asynchronous.
        return ResponseEntity.accepted().build();
    }

    /**
     * Returns all active trust badges for a Worker (P3-04).
     *
     * <p>Only the Worker themselves or an Admin may call this endpoint.
     * Each entry in the returned list is a badge document with fields:
     * {@code badgeId}, {@code awardedAt}, {@code awardedBySystem}, etc.
     *
     * @param uid    Firebase Auth UID of the Worker
     * @param caller the authenticated caller
     * @return HTTP 200 with a list of active badge documents
     */
    @GetMapping("/{uid}/worker/badges")
    public ResponseEntity<List<Map<String, Object>>> getWorkerBadges(
            @PathVariable String uid,
            @AuthenticationPrincipal AuthenticatedUser caller)
            throws InterruptedException, ExecutionException {

        if (!uid.equals(caller.uid()) && !caller.hasRole("admin")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the Worker themselves or an admin may view badges");
        }

        return ResponseEntity.ok(badgeService.getActiveBadges(uid));
    }
}
