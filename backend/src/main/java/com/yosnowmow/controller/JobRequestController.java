package com.yosnowmow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @deprecated Retired in v1.1 (negotiated-marketplace workflow).
 *
 * The sequential-dispatch offer loop that this controller served has been replaced
 * by the bilateral negotiation flow in {@link OfferController}:
 *
 *   POST /api/jobs/{jobId}/offers            — Worker submits an offer
 *   PUT  /api/jobs/{jobId}/offers/{workerId} — Requester responds
 *
 * This class returns HTTP 410 Gone so that any stale clients receive a clear
 * signal rather than a confusing 404 or 405.
 *
 * TODO: Remove once all clients have migrated to the new endpoints.
 */
@Deprecated
@RestController
@RequestMapping("/api/job-requests")
public class JobRequestController {

    @PostMapping("/{requestId}/respond")
    public ResponseEntity<Void> respondToOffer(@PathVariable String requestId) {
        return ResponseEntity.status(HttpStatus.GONE).build();
    }
}
