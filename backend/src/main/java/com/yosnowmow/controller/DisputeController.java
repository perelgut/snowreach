package com.yosnowmow.controller;

import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.yosnowmow.dto.DisputeRequest;
import com.yosnowmow.dto.ResolveDisputeRequest;
import com.yosnowmow.model.Dispute;
import com.yosnowmow.model.Job;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.DisputeService;
import com.yosnowmow.service.JobService;
import com.yosnowmow.service.StorageService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * REST controller for dispute management (P2-01).
 *
 * Base path: {@code /api/disputes}
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>GET  /api/disputes/{disputeId}           — fetch a dispute (party or Admin)</li>
 *   <li>POST /api/disputes/{disputeId}/statement  — add/update a statement (party)</li>
 *   <li>POST /api/disputes/{disputeId}/resolve    — Admin adjudicates the dispute</li>
 * </ul>
 *
 * Opening a dispute is handled by
 * {@code POST /api/jobs/{jobId}/dispute} in {@link JobController}, since it
 * belongs to the job lifecycle namespace.
 *
 * Access control details:
 * <ul>
 *   <li>GET, statement, and evidence endpoints have no role annotation — access is
 *       enforced inside the service or the endpoint itself (must be a party to the job).</li>
 *   <li>Resolve endpoint requires the "admin" role.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/disputes")
public class DisputeController {

    private static final Logger log = LoggerFactory.getLogger(DisputeController.class);

    /** Maximum evidence files per party (Requester and Worker each have this limit). */
    private static final int MAX_EVIDENCE_PER_PARTY = 5;

    private static final String DISPUTES_COLLECTION = "disputes";

    private final DisputeService disputeService;
    private final StorageService storageService;
    private final JobService     jobService;
    private final Firestore      firestore;

    public DisputeController(DisputeService disputeService,
                             StorageService storageService,
                             JobService jobService,
                             Firestore firestore) {
        this.disputeService = disputeService;
        this.storageService = storageService;
        this.jobService     = jobService;
        this.firestore      = firestore;
    }

    /**
     * Returns a dispute document.
     *
     * Access: the Requester or Worker on the associated job, or any Admin.
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<Dispute> getDispute(
            @PathVariable String disputeId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        Dispute dispute = disputeService.getDispute(
                disputeId, caller.uid(), caller.hasRole("admin"));
        return ResponseEntity.ok(dispute);
    }

    /**
     * Adds or replaces the caller's statement on an open dispute.
     *
     * The Requester updates {@code requesterStatement};
     * the Worker updates {@code workerStatement}.
     * Parties may update their statement any time while the dispute is OPEN.
     */
    @PostMapping("/{disputeId}/statement")
    public ResponseEntity<Dispute> addStatement(
            @PathVariable String disputeId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody DisputeRequest req) {

        Dispute dispute = disputeService.addStatement(disputeId, caller.uid(), req);
        return ResponseEntity.ok(dispute);
    }

    /**
     * Admin adjudicates an open dispute.
     *
     * Accepted resolution values:
     * <ul>
     *   <li>{@code RELEASED} — full payment transferred to the Worker</li>
     *   <li>{@code REFUNDED} — full refund issued to the Requester</li>
     *   <li>{@code SPLIT}    — partial transfer + partial refund;
     *                          {@code splitPercentageToWorker} specifies the Worker's share</li>
     * </ul>
     */
    @PostMapping("/{disputeId}/resolve")
    @RequiresRole("admin")
    public ResponseEntity<Dispute> resolveDispute(
            @PathVariable String disputeId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody ResolveDisputeRequest req) {

        Dispute resolved = disputeService.resolveDispute(
                disputeId,
                caller.uid(),
                req.getResolution(),
                req.getSplitPercentageToWorker(),
                req.getAdminNotes());

        return ResponseEntity.status(HttpStatus.OK).body(resolved);
    }

    // ── P2-02: Evidence upload ────────────────────────────────────────────────

    /**
     * Uploads an evidence file to a dispute.
     *
     * <p>Rules:
     * <ul>
     *   <li>Caller must be the Requester or Worker on the job associated with this dispute.</li>
     *   <li>Maximum {@value #MAX_EVIDENCE_PER_PARTY} files per party (Requester and Worker
     *       each have their own limit).</li>
     *   <li>Accepted MIME types: image/jpeg, image/png, application/pdf.</li>
     *   <li>Maximum file size: 20 MB.</li>
     * </ul>
     *
     * <p>The count check reads the current dispute at request time and is subject to a
     * small race window under high concurrency.  Given the low frequency of dispute uploads,
     * this is acceptable for Phase 2.
     *
     * @param disputeId Firestore dispute document ID
     * @param caller    authenticated user (must be a party to the job)
     * @param file      multipart evidence file (form field name: {@code file})
     * @return {@code { url: String, totalEvidenceCount: int }}
     */
    @PostMapping("/{disputeId}/evidence")
    public ResponseEntity<Map<String, Object>> uploadEvidence(
            @PathVariable String disputeId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam("file") MultipartFile file) {

        // a. Load dispute (access check: must be a party or admin).
        Dispute dispute = disputeService.getDispute(
                disputeId, caller.uid(), caller.hasRole("admin"));

        // b. Determine the caller's party role on the associated job.
        Job job = jobService.getJob(dispute.getJobId());

        String partyRole;
        if (caller.uid().equals(job.getRequesterId())) {
            partyRole = "requester";
        } else if (caller.uid().equals(job.getWorkerId())) {
            partyRole = "worker";
        } else {
            // Should not reach here because getDispute already checked access,
            // but guard against admins who are not a party.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only parties to the job may upload evidence");
        }

        // c. Count existing evidence files for this party by checking the storage path
        //    embedded in each Firebase Storage URL.
        //    URL form: ...firebasestorage.../o/disputes%2F{id}%2F{party}%2F...
        String partyPathSegment = "disputes%2F" + disputeId + "%2F" + partyRole + "%2F";
        List<String> allEvidence = dispute.getEvidenceUrls();
        long partyEvidenceCount = (allEvidence != null)
                ? allEvidence.stream().filter(url -> url.contains(partyPathSegment)).count()
                : 0L;

        if (partyEvidenceCount >= MAX_EVIDENCE_PER_PARTY) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Maximum " + MAX_EVIDENCE_PER_PARTY + " evidence files per party.");
        }

        // c. Upload to Firebase Storage.
        String downloadUrl = storageService.uploadDisputeEvidence(disputeId, partyRole, file);

        // d. Atomically append the URL to dispute.evidenceUrls.
        try {
            firestore.collection(DISPUTES_COLLECTION).document(disputeId)
                    .update("evidenceUrls", FieldValue.arrayUnion(downloadUrl))
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to record evidence URL for dispute {}: {}", disputeId,
                    e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to record the uploaded evidence file");
        }

        int totalEvidenceCount = (int) (partyEvidenceCount + 1);
        log.info("Evidence recorded: disputeId={} party={} total={}", disputeId, partyRole,
                totalEvidenceCount);

        // e. Return the URL and updated total for this party.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("url", downloadUrl, "totalEvidenceCount", totalEvidenceCount));
    }
}
