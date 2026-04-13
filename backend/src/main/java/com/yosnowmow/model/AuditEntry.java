package com.yosnowmow.model;

import com.google.cloud.Timestamp;

/**
 * A single entry in the append-only audit log stored in the separate
 * audit Firestore project ({@code yosnowmow-audit}).
 *
 * Entries are chained via SHA-256: each entry records the hash of the
 * previous entry, so any tampering with historical records breaks the chain.
 *
 * Full hash-chain verification is implemented in P1-20 (AuditLogService).
 * This model is used from P1-08 onward wherever state-changing writes occur.
 *
 * Matches spec §8.1 audit log schema.
 */
public class AuditEntry {

    /** Auto-generated Firestore document ID. */
    private String entryId;

    /** UID of the actor who triggered this event; "system" for automated actions. */
    private String actorUid;

    /** Verb describing what happened (e.g. "JOB_CREATED", "JOB_STATUS_CHANGED"). */
    private String action;

    /** Entity type (e.g. "job", "user", "worker"). */
    private String entityType;

    /** Entity ID (e.g. the jobId or userId). */
    private String entityId;

    /** JSON snapshot of the entity before the action; null for create events. */
    private String beforeJson;

    /** JSON snapshot of the entity after the action. */
    private String afterJson;

    /** SHA-256 of the previous entry's hash — forms the chain. */
    private String previousHash;

    /**
     * SHA-256 of (previousHash + timestamp + actorUid + action + entityId +
     * beforeJson + afterJson), using null-byte separators.
     */
    private String entryHash;

    private Timestamp timestamp;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Required by Firestore deserialisation. */
    public AuditEntry() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getEntryId() { return entryId; }
    public void setEntryId(String entryId) { this.entryId = entryId; }

    public String getActorUid() { return actorUid; }
    public void setActorUid(String actorUid) { this.actorUid = actorUid; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }

    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }

    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }

    public String getEntryHash() { return entryHash; }
    public void setEntryHash(String entryHash) { this.entryHash = entryHash; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
