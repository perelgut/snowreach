package com.yosnowmow.controller;

import com.yosnowmow.dto.RatingRequest;
import com.yosnowmow.model.Rating;
import com.yosnowmow.security.AuthenticatedUser;
import com.yosnowmow.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for ratings and reviews (P1-16).
 *
 * Base path: {@code /api/jobs/{jobId}}
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>POST /api/jobs/{jobId}/rating  — submit a rating (Requester or Worker)</li>
 *   <li>GET  /api/jobs/{jobId}/ratings — list ratings for a job</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/jobs/{jobId}")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /**
     * Submits a rating for a completed job.
     *
     * <p>The caller must be either the Requester or the assigned Worker on this job.
     * The raterRole is determined server-side from the caller's UID — callers cannot
     * self-select their role.
     *
     * <p>A 409 is returned if the job is not yet COMPLETE, or if the caller has
     * already submitted a rating for this job.
     *
     * <p>When both parties have rated a job that is still COMPLETE, the payment is
     * released immediately (bypassing the 4-hour auto-release timer).
     *
     * @param jobId  Firestore job document ID
     * @param caller authenticated caller (Requester or Worker)
     * @param req    rating payload
     * @return the persisted {@link Rating}
     */
    @PostMapping("/rating")
    public ResponseEntity<Rating> submitRating(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody RatingRequest req) {

        Rating rating = ratingService.submitRating(jobId, caller.uid(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(rating);
    }

    /**
     * Returns all ratings for a job.
     *
     * <p>No role restriction: Requesters, Workers, and Admins may all call this
     * endpoint.  Business-layer visibility rules (e.g. hiding reviewer identity)
     * are deferred to Phase 2.
     *
     * @param jobId  Firestore job document ID
     * @param caller authenticated caller
     * @return list of ratings (0–2 documents per job)
     */
    @GetMapping("/ratings")
    public ResponseEntity<List<Rating>> getRatings(
            @PathVariable String jobId,
            @AuthenticationPrincipal AuthenticatedUser caller) {

        List<Rating> ratings = ratingService.getRatingsForJob(jobId);
        return ResponseEntity.ok(ratings);
    }
}
