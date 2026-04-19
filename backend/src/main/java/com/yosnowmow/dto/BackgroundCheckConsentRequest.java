package com.yosnowmow.dto;

import jakarta.validation.constraints.AssertTrue;

/**
 * Request body for {@code POST /api/users/{uid}/worker/background-check}.
 *
 * <p>The Worker must explicitly set {@code consented: true} before a background
 * check is submitted to Certn.  This provides a clear audit trail of informed consent.
 */
public class BackgroundCheckConsentRequest {

    /**
     * Must be {@code true}; the Worker must explicitly consent.
     * Validated by {@code @AssertTrue} — a {@code false} value is rejected with 400.
     */
    @AssertTrue(message = "consented must be true — explicit consent is required to submit a background check")
    private boolean consented;

    public boolean isConsented() { return consented; }
    public void setConsented(boolean consented) { this.consented = consented; }
}
