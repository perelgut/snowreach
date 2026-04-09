package com.yosnowmow.security;

import java.util.List;

/**
 * Immutable record representing the currently authenticated user.
 * Populated by FirebaseTokenFilter on every verified request and stored
 * in the Spring SecurityContext.
 *
 * Roles are read from the Firebase custom claim "roles" set when the
 * user document is created in Firestore (P1-05).
 */
public record AuthenticatedUser(
        String uid,
        String email,
        List<String> roles
) {

    /**
     * Returns true if this user holds the given role.
     * Case-sensitive — roles are stored uppercase (e.g. "ADMIN", "WORKER", "REQUESTER").
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
