package com.yosnowmow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/users}.
 *
 * The caller must already be authenticated via Firebase Auth (i.e. the
 * FirebaseTokenFilter has verified the ID token).  The UID comes from the
 * SecurityContext — it is never supplied in the body.
 *
 * Validation annotations are enforced by @Valid in the controller.
 */
public class CreateUserRequest {

    /** Full display name. */
    @NotBlank(message = "name is required")
    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    /**
     * Date of birth in ISO 8601 format (YYYY-MM-DD).
     * Age verification (18+) is performed in UserService.
     */
    @NotBlank(message = "dateOfBirth is required")
    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateOfBirth must be in YYYY-MM-DD format")
    private String dateOfBirth;

    /** Version of the Terms of Service the user is accepting. */
    @NotBlank(message = "tosVersion is required")
    private String tosVersion;

    /** Version of the Privacy Policy the user is accepting. */
    @NotBlank(message = "privacyPolicyVersion is required")
    private String privacyPolicyVersion;

    /**
     * Initial roles — must be a non-empty subset of ["requester", "worker"].
     * Users cannot self-assign "admin".
     */
    @NotEmpty(message = "at least one role is required")
    private List<String> roles;

    /** Optional phone number (E.164 format preferred, e.g. +16135550100). */
    private String phoneNumber;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getTosVersion() { return tosVersion; }
    public void setTosVersion(String tosVersion) { this.tosVersion = tosVersion; }

    public String getPrivacyPolicyVersion() { return privacyPolicyVersion; }
    public void setPrivacyPolicyVersion(String privacyPolicyVersion) { this.privacyPolicyVersion = privacyPolicyVersion; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
