package com.yosnowmow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.model.AuditEntry;
import com.yosnowmow.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

/**
 * Append-only audit log service.
 *
 * Every state-changing Firestore write in the system must call
 * {@link #write(String, String, String, String, Object, Object)}
 * <em>before</em> the write itself.  This ensures the audit trail captures
 * the before state even if the main write fails.
 *
 * Entries are written to a <strong>separate Firestore project</strong>
 * ({@code yosnowmow-audit}), collection {@code auditLog}.
 *
 * Hash chain (spec §8.1):
 *   Each entry stores:
 *     {@code previousHash} — the {@code entryHash} of the most recent entry
 *                           for the same entity (or "GENESIS" if first)
 *     {@code entryHash}    — SHA-256(previousHash \0 timestamp \0 actorUid \0
 *                                    action \0 entityId \0 beforeJson \0 afterJson)
 *
 * Full chain verification (daily integrity check) is implemented in P1-20.
 *
 * This service is non-fatal: audit failures are logged but do NOT block the
 * main operation.  An audit gap is better than a user-facing 500 error.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final String AUDIT_COLLECTION = "auditLog";

    /** Sentinel value used as previousHash for the first entry of an entity. */
    private static final String GENESIS_HASH = "GENESIS";

    private final Firestore auditFirestore;
    private final ObjectMapper objectMapper;

    public AuditLogService(@Qualifier("auditFirestore") Firestore auditFirestore,
                           ObjectMapper objectMapper) {
        this.auditFirestore = auditFirestore;
        this.objectMapper = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Writes an audit log entry for a state-changing event.
     *
     * Call this <strong>before</strong> the corresponding Firestore write.
     *
     * @param actorUid   UID of the actor; use "system" for automated actions
     * @param action     verb describing the event (e.g. "JOB_CREATED")
     * @param entityType entity collection name (e.g. "job", "user")
     * @param entityId   entity document ID
     * @param before     entity state before the action; null for create events
     * @param after      entity state after the action
     */
    public void write(String actorUid,
                      String action,
                      String entityType,
                      String entityId,
                      Object before,
                      Object after) {
        try {
            String beforeJson = toJson(before);
            String afterJson  = toJson(after);
            String prevHash   = fetchLatestHash(entityType, entityId);
            Timestamp now     = Timestamp.now();

            String entryHash = HashUtils.sha256Parts(
                    prevHash,
                    String.valueOf(now.getSeconds()),
                    actorUid,
                    action,
                    entityId,
                    beforeJson == null ? "" : beforeJson,
                    afterJson  == null ? "" : afterJson
            );

            String entryId = UUID.randomUUID().toString();

            Map<String, Object> doc = new HashMap<>();
            doc.put("entryId",      entryId);
            doc.put("actorUid",     actorUid);
            doc.put("action",       action);
            doc.put("entityType",   entityType);
            doc.put("entityId",     entityId);
            doc.put("beforeJson",   beforeJson);
            doc.put("afterJson",    afterJson);
            doc.put("previousHash", prevHash);
            doc.put("entryHash",    entryHash);
            doc.put("timestamp",    now);

            auditFirestore.collection(AUDIT_COLLECTION)
                          .document(entryId)
                          .set(doc)
                          .get();

        } catch (Exception e) {
            // Non-fatal: log the failure but do not propagate.
            // An audit gap is preferable to blocking a user operation.
            log.error("AUDIT WRITE FAILED — action={} entity={}/{} actor={}: {}",
                    action, entityType, entityId, actorUid, e.getMessage(), e);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches the {@code entryHash} of the most recent audit entry for the given
     * entity (ordered by timestamp descending).
     *
     * Returns {@link #GENESIS_HASH} if no prior entry exists.
     */
    private String fetchLatestHash(String entityType, String entityId) {
        try {
            QuerySnapshot snap = auditFirestore.collection(AUDIT_COLLECTION)
                    .whereEqualTo("entityType", entityType)
                    .whereEqualTo("entityId", entityId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .get();

            List<QueryDocumentSnapshot> docs = snap.getDocuments();
            if (docs.isEmpty()) {
                return GENESIS_HASH;
            }
            String hash = docs.get(0).getString("entryHash");
            return hash != null ? hash : GENESIS_HASH;

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.warn("Could not fetch latest audit hash for {}/{}: {}",
                    entityType, entityId, e.getMessage());
            // Fall back to GENESIS so the write can proceed
            return GENESIS_HASH;
        }
    }

    /** Serialises an object to JSON; returns null if the object is null. */
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise object to JSON for audit log: {}", e.getMessage());
            return "{\"error\":\"serialisation_failed\"}";
        }
    }
}
