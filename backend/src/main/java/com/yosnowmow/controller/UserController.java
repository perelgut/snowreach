package com.yosnowmow.controller;

import com.yosnowmow.dto.CreateUserRequest;
import com.yosnowmow.dto.UpdateUserRequest;
import com.yosnowmow.model.User;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user registration and profile management.
 *
 * Base path: {@code /api/users}
 *
 * Endpoints:
 *   POST   /api/users          — register a new user (caller's UID from token)
 *   GET    /api/users/{id}     — fetch a user profile
 *   PATCH  /api/users/{id}     — update a user profile (partial update)
 *
 * Access control:
 *   POST  — any authenticated Firebase user (UID comes from the verified token)
 *   GET   — own profile or ADMIN
 *   PATCH — own profile or ADMIN
 *
 * The FirebaseTokenFilter guarantees that {@code caller} is never null on any
 * endpoint reaching this controller (all paths under /api/** require a token).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Registers a new user profile.
     *
     * The UID is taken from the verified Firebase ID token — the client
     * must NOT supply it in the request body.
     *
     * @param caller the authenticated caller, injected from the SecurityContext
     * @param req    the registration payload
     * @return HTTP 201 with the created User document
     */
    @PostMapping
    public ResponseEntity<User> register(
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CreateUserRequest req) {

        User created = userService.createUser(caller, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Returns the user profile for the given ID.
     *
     * Users may only fetch their own profile unless they hold the ADMIN role.
     *
     * @param userId the Firebase Auth UID
     * @param caller the authenticated caller, injected from the SecurityContext
     * @return HTTP 200 with the User document
     */
    @GetMapping("/{userId}")
    public ResponseEntity<User> getUser(
            @PathVariable String userId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        requireSelfOrAdmin(caller, userId);
        return ResponseEntity.ok(userService.getUser(userId));
    }

    /**
     * Partially updates the user profile for the given ID.
     *
     * Only non-null fields in the request body are applied.
     * Users may only update their own profile unless they hold the ADMIN role.
     *
     * @param userId the Firebase Auth UID
     * @param caller the authenticated caller, injected from the SecurityContext
     * @param req    fields to update
     * @return HTTP 200 with the updated User document
     */
    @PatchMapping("/{userId}")
    public ResponseEntity<User> updateUser(
            @PathVariable String userId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody UpdateUserRequest req) {

        requireSelfOrAdmin(caller, userId);
        return ResponseEntity.ok(userService.updateUser(userId, req));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Throws {@link AccessDeniedException} if {@code caller} is neither the
     * resource owner nor an admin.  Checked inline so the pattern is explicit
     * and auditable in this controller rather than hidden in annotations.
     */
    private void requireSelfOrAdmin(AuthenticatedUser caller, String targetUserId) {
        if (!caller.uid().equals(targetUserId) && !caller.hasRole("admin")) {
            throw new AccessDeniedException(
                    "You may only access your own profile");
        }
    }
}
