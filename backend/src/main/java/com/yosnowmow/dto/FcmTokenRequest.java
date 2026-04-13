package com.yosnowmow.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/users/{uid}/fcm-token}.
 *
 * Sent by the React client after the user grants notification permission and
 * FCM returns a device token.  A null or empty token is accepted and clears
 * any existing token on the user document (e.g. after the user revokes permission).
 */
public class FcmTokenRequest {

    /** FCM device token returned by {@code getToken()} in the Firebase JS SDK. */
    @Size(max = 256, message = "fcmToken must not exceed 256 characters")
    private String fcmToken;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }
}
