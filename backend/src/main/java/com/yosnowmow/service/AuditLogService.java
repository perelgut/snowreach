package com.yosnowmow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.Transaction;
import com.yosnowmow.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Append-only audit log service with SHA-256 hash chaining (P1-20).
 *
 * Every state-changing event in the system is recorded here before the
 * corresponding Firestore write.  Entries are chained globally: each entry
 * stores the SHA-256 hash of the previous entry in the whole log, not just
 * the previous entry for the same entity.  Any tampering with any single
 * record breaks every subsequent hash in the chain.
 *
 * <h3>Global chain design</h3>
 * Two meta-documents live in the {@code auditLog} collection alongside the
 * regular entries:
 * <ul>
 *   <li>{@code __chain_head} — {@code { hash: "...", lastUpdated: Timestamp }}</li>
 *   <li>{@code __seq_counter} — {@code { value: N }}</li>
 * </ul>
 * Every call to {@link #write} runs a Firestore transaction that reads both
 * meta-documents, writes the new entry, and updates both meta-documents
 * atomically.  This guarantees a consistent global sequence even under
 * concurrent writes.
 *
 * <h3>Hash formula</h3>
 * <pre>
 *   entryHash = SHA-256(previousHash \0 timestamp.seconds \0 actorUid \0
 *                       action \0 entityId \0 beforeJson \0 afterJson)
 * </pre>
 * Null-byte separators prevent adjacent fields from blending.
 *
 * <h3>Failure behaviour</h3>
 * Audit failures are <em>non-fatal</em>: every exception is caught, logged,
 * and swallowed.  An audit gap is less harmful than blocking a user operation.
 *
 * <h3>Daily integrity check</h3>
 * {@link #verifyPreviousDay()} is called by {@link com.yosnowmow.scheduler.AuditIntegrityJob}
 * at 02:00 America/Toronto.  It re-computes the hash for every entry written
 * the previous day and verifies both the hash correctness and chain linkage.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    /** Firestore collection for all audit entries and meta-documents. */
    private static final String AUDIT_COLLECTION = "auditLog";

    /** Document ID of the global chain-head meta-document. */
    private static final String CHAIN_HEAD_DOC = "__chain_head";

    /** Document ID of the global sequence-counter meta-document. */
    private static final String SEQ_COUNTER_DOC = "__seq_counter";

    /**
     * Hash used as {@code previousHash} for the very first entry in the log.
     * 64 hex zeros — the same length as a real SHA-256 hex digest.
     */
    static final String GENESIS_HASH =
            "0000000000000000000000000000000000000000000000000000000000000000";

    /** Timezone for "yesterday" boundary calculations in the integrity check. */
    private static final ZoneId ONTARIO_ZONE = ZoneId.of("America/Toronto");

    private final Firestore    auditFirestore;
    private final ObjectMapper objectMapper;

    public AuditLogService(@Qualifier("auditFirestore") Firestore auditFirestore,
                           ObjectMapper objectMapper) {
        this.auditFirestore = auditFirestore;
        this.objectMapper   = objectMapper;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Writes a single entry to the audit log.
     *
     * Call this <strong>before</strong> the corresponding operational write so
     * the before-state is captured even if the main write fails.
     *
     * Runs as a Firestore transaction:
     * <ol>
     *   <li>Reads the global chain head ({@code __chain_head}) and sequence counter
     *       ({@code __seq_counter}) atomically.</li>
     *   <li>Computes the new entry hash.</li>
     *   <li>Writes the entry document and updates both meta-documents in the same
     *       transaction.</li>
     * </ol>
     *
     * @param actorUid   Firebase UID of the actor; {@code "system"} for automated events
     * @param action     verb describing the event (e.g. {@code "JOB_CREATED"})
     * @param entityType entity collection name (e.g. {@code "job"}, {@code "user"})
     * @param entityId   entity document ID
     * @param before     entity state before the action; {@code null} for create events
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
            Timestamp now     = Timestamp.now();
            String entryId    = UUID.randomUUID().toString();

            DocumentReference chainHeadRef  = auditFirestore.collection(AUDIT_COLLECTION).document(CHAIN_HEAD_DOC);
            DocumentReference seqCounterRef = auditFirestore.collection(AUDIT_COLLECTION).document(SEQ_COUNTER_DOC);
            DocumentReference entryRef      = auditFirestore.collection(AUDIT_COLLECTION).document(entryId);

            auditFirestore.runTransaction((Transaction.Function<Void>) transaction -> {

                // ── 1. Read chain head and sequence counter ───────────────────
                DocumentSnapshot chainSnap = transaction.get(chainHeadRef).get();
                DocumentSnapshot seqSnap   = transaction.get(seqCounterRef).get();

                String prevHash = (chainSnap.exists() && chainSnap.getString("hash") != null)
                        ? chainSnap.getString("hash")
                        : GENESIS_HASH;

                Long prevSeq    = seqSnap.exists() ? seqSnap.getLong("value") : null;
                long newSeq     = (prevSeq != null) ? prevSeq + 1 : 1;

                // ── 2. Compute this entry's hash ──────────────────────────────
                String entryHash = HashUtils.sha256Parts(
                        prevHash,
                        String.valueOf(now.getSeconds()),
                        actorUid   != null ? actorUid   : "",
                        action     != null ? action     : "",
                        entityId   != null ? entityId   : "",
                        beforeJson != null ? beforeJson : "",
                        afterJson  != null ? afterJson  : ""
                );

                // ── 3. Build entry document ───────────────────────────────────
                Map<String, Object> doc = new HashMap<>();
                doc.put("entryId",        entryId);
                doc.put("actorUid",       actorUid);
                doc.put("action",         action);
                doc.put("entityType",     entityType);
                doc.put("entityId",       entityId);
                doc.put("beforeJson",     beforeJson);
                doc.put("afterJson",      afterJson);
                doc.put("previousHash",   prevHash);
                doc.put("entryHash",      entryHash);
                doc.put("sequenceNumber", newSeq);
                doc.put("timestamp",      now);

                // ── 4. Write entry and update meta-documents ──────────────────
                transaction.set(entryRef, doc);

                transaction.set(chainHeadRef, Map.of(
                        "hash",        entryHash,
                        "lastUpdated", now
                ));
                transaction.set(seqCounterRef, Map.of(
                        "value", newSeq
                ));

                return null;

            }).get(); // wait for the transaction to commit

        } catch (Exception e) {
            // Non-fatal — an audit gap is preferable to a user-facing 500 error.
            log.error("AUDIT WRITE FAILED — action={} entity={}/{} actor={}: {}",
                    action, entityType, entityId, actorUid, e.getMessage(), e);
        }
    }

    // ── Integrity verification ────────────────────────────────────────────────

    /**
     * Verifies the integrity of all audit entries written during the previous
     * calendar day (America/Toronto timezone).
     *
     * For each entry, this method:
     * <ol>
     *   <li>Re-computes the SHA-256 hash from the stored fields.</li>
     *   <li>Compares it to the stored {@code entryHash}.</li>
     *   <li>Verifies that {@code entry[i].previousHash == entry[i-1].entryHash}
     *       for consecutive entries within the day.</li>
     * </ol>
     *
     * @return an {@link IntegrityReport} summarising the result
     */
    public IntegrityReport verifyPreviousDay() {
        // Midnight boundaries for "yesterday" in Ontario time
        LocalDate yesterday   = LocalDate.now(ONTARIO_ZONE).minusDays(1);
        ZonedDateTime dayStart = yesterday.atStartOfDay(ONTARIO_ZONE);
        ZonedDateTime dayEnd   = dayStart.plusDays(1);

        Timestamp tsStart = Timestamp.ofTimeSecondsAndNanos(dayStart.toEpochSecond(), 0);
        Timestamp tsEnd   = Timestamp.ofTimeSecondsAndNanos(dayEnd.toEpochSecond(),   0);

        try {
            // Fetch all entries from yesterday, ordered by sequenceNumber.
            // Firestore requires the range-filter field in the first orderBy when
            // a second orderBy is added; using timestamp as primary sort here is
            // equivalent because sequence numbers are assigned in time order.
            QuerySnapshot snap = auditFirestore.collection(AUDIT_COLLECTION)
                    .whereGreaterThanOrEqualTo("timestamp", tsStart)
                    .whereLessThan("timestamp", tsEnd)
                    .orderBy("timestamp",       Query.Direction.ASCENDING)
                    .orderBy("sequenceNumber",  Query.Direction.ASCENDING)
                    .get()
                    .get();

            List<QueryDocumentSnapshot> docs = snap.getDocuments();
            int total      = docs.size();
            int mismatches = 0;

            log.info("Audit integrity check — verifying {} entries for {} (Ontario)",
                    total, yesterday);

            String prevEntryHash = null; // entryHash of the previous document in iteration

            for (int i = 0; i < docs.size(); i++) {
                QueryDocumentSnapshot doc = docs.get(i);
                String docId = doc.getId();

                // Skip the meta-documents if they happen to fall in range (they won't,
                // but be defensive — meta-docs don't have the timestamp field).
                if (CHAIN_HEAD_DOC.equals(docId) || SEQ_COUNTER_DOC.equals(docId)) {
                    continue;
                }

                String storedHash  = doc.getString("entryHash");
                String prevHash    = doc.getString("previousHash");
                Timestamp ts       = doc.getTimestamp("timestamp");
                String actorUid    = doc.getString("actorUid");
                String action      = doc.getString("action");
                String entityId    = doc.getString("entityId");
                String beforeJson  = doc.getString("beforeJson");
                String afterJson   = doc.getString("afterJson");
                Long   seqNum      = doc.getLong("sequenceNumber");

                // Re-compute the hash using the same formula as write()
                String recomputed = HashUtils.sha256Parts(
                        prevHash   != null ? prevHash   : "",
                        ts         != null ? String.valueOf(ts.getSeconds()) : "",
                        actorUid   != null ? actorUid   : "",
                        action     != null ? action     : "",
                        entityId   != null ? entityId   : "",
                        beforeJson != null ? beforeJson : "",
                        afterJson  != null ? afterJson  : ""
                );

                // ── Check 1: hash correctness ──────────────────────────────────
                if (!recomputed.equals(storedHash)) {
                    mismatches++;
                    log.error("AUDIT INTEGRITY MISMATCH — entryId={} seq={}: "
                                    + "stored={} recomputed={}",
                            docId, seqNum, storedHash, recomputed);
                }

                // ── Check 2: chain linkage (consecutive entries within the day) ─
                if (prevEntryHash != null && !prevEntryHash.equals(prevHash)) {
                    mismatches++;
                    log.error("AUDIT CHAIN BREAK — entryId={} seq={}: "
                                    + "expected previousHash={} stored={}",
                            docId, seqNum, prevEntryHash, prevHash);
                }

                prevEntryHash = storedHash; // advance for next iteration
            }

            if (mismatches == 0) {
                log.info("Audit integrity check PASSED — {} entries verified for {}",
                        total, yesterday);
            } else {
                log.error("Audit integrity check FAILED — {}/{} mismatches for {}",
                        mismatches, total, yesterday);
            }

            return new IntegrityReport(total, mismatches, yesterday.toString());

        } catch (Exception e) {
            log.error("Audit integrity check ERRORED for {}: {}", yesterday, e.getMessage(), e);
            return new IntegrityReport(0, -1, yesterday.toString()); // -1 = check did not complete
        }
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    /**
     * Summary result returned by {@link #verifyPreviousDay()}.
     *
     * {@code mismatches == -1} means the check itself failed (query error, etc.).
     */
    public static class IntegrityReport {
        private final int    totalChecked;
        private final int    mismatches;
        private final String date;

        public IntegrityReport(int totalChecked, int mismatches, String date) {
            this.totalChecked = totalChecked;
            this.mismatches   = mismatches;
            this.date         = date;
        }

        public int    getTotalChecked() { return totalChecked; }
        public int    getMismatches()   { return mismatches; }
        public String getDate()         { return date; }
        public boolean passed()         { return mismatches == 0; }
        public boolean errored()        { return mismatches == -1; }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Serialises an object to a JSON string; returns {@code null} if the object is null. */
    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Could not serialise object to JSON for audit entry: {}", e.getMessage());
            return "{\"error\":\"serialisation_failed\"}";
        }
    }
}
