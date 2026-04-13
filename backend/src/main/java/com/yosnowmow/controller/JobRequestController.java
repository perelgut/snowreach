package com.yosnowmow.controller;

import com.yosnowmow.dto.RespondToJobRequestDto;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.DispatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Worker responses to job-offer dispatch requests.
 *
 * Base path: {@code /api/job-requests}
 *
 * Endpoints:
 *   POST /api/job-requests/{requestId}/respond — Worker accepts or declines a job offer
 *
 * The {@code requestId} is the composite key "{jobId}_{workerId}" stored in the
 * {@code jobRequests} Firestore collection.  Workers receive this ID via the push
 * notification sent by DispatchService (P1-10; full wiring in P1-18).
 */
@RestController
@RequestMapping("/api/job-requests")
public class JobRequestController {

    private final DispatchService dispatchService;

    public JobRequestController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    /**
     * Worker accepts or declines a pending job offer.
     *
     * The caller must hold the "worker" role and must be the intended recipient
     * of the offer (verified by DispatchService against the jobRequest document).
     *
     * Response codes:
     *   200 — response recorded
     *   403 — caller is not the offer recipient
     *   404 — no jobRequest found for this requestId
     *   409 — offer is no longer PENDING (already accepted, declined, or expired)
     *
     * @param requestId composite offer ID "{jobId}_{workerId}"
     * @param caller    authenticated caller (from Firebase ID token)
     * @param req       body containing {@code accepted: true|false}
     */
    @PostMapping("/{requestId}/respond")
    @RequiresRole("worker")
    public ResponseEntity<Void> respondToOffer(
            @PathVariable String requestId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @RequestBody RespondToJobRequestDto req) {

        dispatchService.handleWorkerResponse(requestId, caller.uid(), req.isAccepted());
        return ResponseEntity.ok().build();
    }
}
