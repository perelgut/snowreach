package com.yosnowmow.controller;

import com.yosnowmow.dto.OfferRequest;
import com.yosnowmow.model.JobOffer;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.security.RequiresRole;
import com.yosnowmow.service.OfferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for the negotiated-marketplace offer flow.
 *
 * Base path: {@code /api/jobs/{jobId}/offers}
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>GET  /api/jobs/{jobId}/offers              — list all offers on a job (admin or requester)</li>
 *   <li>POST /api/jobs/{jobId}/offers              — Worker submits an offer (accept/counter/photo request)</li>
 *   <li>PUT  /api/jobs/{jobId}/offers/{workerId}   — Requester responds to a Worker's offer</li>
 * </ul>
 *
 * Spec ref: §16.6
 */
@RestController
@RequestMapping("/api/jobs/{jobId}/offers")
public class OfferController {

    private final OfferService offerService;

    public OfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    /**
     * Lists all offer documents for a job.
     *
     * Accessible to the Requester who owns the job and to Admins.
     * (The worker can see their own offer via the single-worker path.)
     */
    @GetMapping
    @RequiresRole("requester")
    public ResponseEntity<List<JobOffer>> listOffers(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        List<JobOffer> offers = offerService.getOffersForJob(jobId);
        return ResponseEntity.ok(offers);
    }

    /**
     * Worker submits an offer on a POSTED or NEGOTIATING job.
     *
     * <p>Actions: "accept" | "counter" | "photo_request" | "withdraw"
     *
     * <p>On a Worker's first "accept" at the posted price, this is equivalent to
     * accepting the Requester's original price without negotiation.  On "counter",
     * the Requester is notified to respond.
     */
    @PostMapping
    @RequiresRole("worker")
    public ResponseEntity<JobOffer> workerSubmitOffer(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody OfferRequest req) {

        JobOffer offer = offerService.workerSubmitOffer(jobId, caller.uid(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(offer);
    }

    /**
     * Requester responds to a specific Worker's offer.
     *
     * <p>Actions: "accept" | "counter" | "reject"
     *
     * <p>On "accept", if the Worker's latest action was also "accept", the job moves
     * to AGREED and both parties are notified to proceed with escrow.
     *
     * <p>On "reject", the Worker is blocked from this job and their rolling 90-day
     * rejection counter is incremented.
     */
    @PutMapping("/{workerId}")
    @RequiresRole("requester")
    public ResponseEntity<JobOffer> requesterRespond(
            @PathVariable String jobId,
            @PathVariable String workerId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody OfferRequest req) {

        JobOffer offer = offerService.requesterRespondToOffer(jobId, workerId, caller.uid(), req);
        return ResponseEntity.ok(offer);
    }
}
