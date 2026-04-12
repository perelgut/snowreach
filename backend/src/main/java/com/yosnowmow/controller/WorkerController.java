package com.yosnowmow.controller;

import com.yosnowmow.dto.WorkerProfileRequest;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.WorkerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
 */
@RestController
@RequestMapping("/api/users")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
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
}
