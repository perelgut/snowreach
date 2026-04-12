package com.yosnowmow.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code PATCH /api/users/{id}}.
 *
 * All fields are optional — only non-null values are applied.
 * Role changes and account status changes are admin-only and handled
 * via separate endpoints (not this DTO).
 */
public class UpdateUserRequest {

    @Size(max = 100, message = "name must not exceed 100 characters")
    private String name;

    @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "dateOfBirth must be in YYYY-MM-DD format")
    private String dateOfBirth;

    /** Optional phone number (E.164 format preferred). */
    private String phoneNumber;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}
