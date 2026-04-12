package com.yosnowmow.model;

import com.google.cloud.Timestamp;

import java.util.List;

/**
 * Represents a document from the Firestore {@code users/{userId}} collection.
 *
 * This is a plain Java object used for serialisation/deserialisation against
 * Firestore.  All fields match the spec §3.1 schema exactly.  Phase 1 only
 * includes the core fields; the full worker/requester sub-objects are populated
 * by WorkerService (P1-06) and by the job/payment flows in later tasks.
 *
 * Field naming follows the Firestore document convention (camelCase).
 */
public class User {

    // ── Identity ─────────────────────────────────────────────────────────────

    /** Firebase Auth UID — also the Firestore document key. */
    private String uid;

    private String email;

    /** Full display name. */
    private String name;

    /** ISO 8601 date string (YYYY-MM-DD). */
    private String dateOfBirth;

    /** Timestamp when age was confirmed (set server-side). */
    private Timestamp ageVerifiedAt;

    // ── Terms & Privacy ──────────────────────────────────────────────────────

    /** Version of the Terms of Service the user accepted (e.g. "1.0"). */
    private String tosVersion;

    private Timestamp tosAcceptedAt;

    private String privacyPolicyVersion;

    private Timestamp privacyPolicyAcceptedAt;

    // ── Roles & Status ───────────────────────────────────────────────────────

    /**
     * Roles held by this user — subset of ["requester", "worker", "admin"].
     * Mirrored as a Firebase custom claim on every write.
     */
    private List<String> roles;

    /**
     * Account lifecycle state.
     * One of: "active" | "suspended" | "banned"
     */
    private String accountStatus;

    private String suspendedReason;

    // ── Contact ──────────────────────────────────────────────────────────────

    private String phoneNumber;

    private Timestamp phoneVerifiedAt;

    // ── Geography ────────────────────────────────────────────────────────────

    /** Zone identifier if the user's address is within a launch zone; null otherwise. */
    private String launchZoneId;

    // ── Timestamps ───────────────────────────────────────────────────────────

    private Timestamp createdAt;

    private Timestamp updatedAt;

    // ── Role sub-objects ──────────────────────────────────────────────────────

    /**
     * Worker-specific profile data.
     * Present only when this user holds the "worker" role.
     * Populated and updated exclusively by WorkerService.
     */
    private WorkerProfile worker;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Required by Firestore deserialisation. */
    public User() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public Timestamp getAgeVerifiedAt() { return ageVerifiedAt; }
    public void setAgeVerifiedAt(Timestamp ageVerifiedAt) { this.ageVerifiedAt = ageVerifiedAt; }

    public String getTosVersion() { return tosVersion; }
    public void setTosVersion(String tosVersion) { this.tosVersion = tosVersion; }

    public Timestamp getTosAcceptedAt() { return tosAcceptedAt; }
    public void setTosAcceptedAt(Timestamp tosAcceptedAt) { this.tosAcceptedAt = tosAcceptedAt; }

    public String getPrivacyPolicyVersion() { return privacyPolicyVersion; }
    public void setPrivacyPolicyVersion(String privacyPolicyVersion) { this.privacyPolicyVersion = privacyPolicyVersion; }

    public Timestamp getPrivacyPolicyAcceptedAt() { return privacyPolicyAcceptedAt; }
    public void setPrivacyPolicyAcceptedAt(Timestamp privacyPolicyAcceptedAt) { this.privacyPolicyAcceptedAt = privacyPolicyAcceptedAt; }

    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }

    public String getAccountStatus() { return accountStatus; }
    public void setAccountStatus(String accountStatus) { this.accountStatus = accountStatus; }

    public String getSuspendedReason() { return suspendedReason; }
    public void setSuspendedReason(String suspendedReason) { this.suspendedReason = suspendedReason; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Timestamp getPhoneVerifiedAt() { return phoneVerifiedAt; }
    public void setPhoneVerifiedAt(Timestamp phoneVerifiedAt) { this.phoneVerifiedAt = phoneVerifiedAt; }

    public String getLaunchZoneId() { return launchZoneId; }
    public void setLaunchZoneId(String launchZoneId) { this.launchZoneId = launchZoneId; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public WorkerProfile getWorker() { return worker; }
    public void setWorker(WorkerProfile worker) { this.worker = worker; }
}
