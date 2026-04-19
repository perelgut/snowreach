package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.yosnowmow.dto.RatingRequest;
import com.yosnowmow.exception.InvalidTransitionException;
import com.yosnowmow.exception.JobNotFoundException;
import com.yosnowmow.model.Job;
import com.yosnowmow.model.Rating;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Business logic for job ratings and reviews (P1-16).
 *
 * <h3>Rating flow</h3>
 * <ol>
 *   <li>Job reaches COMPLETE state.</li>
 *   <li>Requester and Worker each submit a rating via
 *       {@code POST /api/jobs/{jobId}/rating}.</li>
 *   <li>When both ratings exist, {@link #checkAndRelease} transitions the job to
 *       RELEASED and triggers the Stripe payout.  This replaces the 4-hour
 *       auto-release timer if both parties rate before it fires.</li>
 *   <li>If only one party rates (or neither), the Quartz {@code DisputeTimerJob}
 *       releases the payment automatically after 4 hours.</li>
 * </ol>
 *
 * <h3>Worker average rating</h3>
 * When the Requester submits a rating, the Worker's {@code worker.rating} and
 * {@code worker.ratingCount} are updated atomically via a Firestore transaction.
 * The average is shown publicly once the Worker has 10+ completed jobs (spec §3.1).
 */
@Service
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private static final String RATINGS_COLLECTION = "ratings";

    /**
     * Statuses from which a rating may be submitted.
     * Ratings are meaningful at COMPLETE; we also allow them after auto-release
     * (RELEASED, SETTLED) for goodwill feedback even though payment is already settled.
     */
    private static final Set<String> RATEABLE_STATUSES =
            Set.of("PENDING_APPROVAL", "RELEASED", "SETTLED");

    private final Firestore      firestore;
    private final JobService     jobService;
    private final PaymentService paymentService;
    private final BadgeService   badgeService;

    public RatingService(Firestore firestore,
                         JobService jobService,
                         PaymentService paymentService,
                         BadgeService badgeService) {
        this.firestore      = firestore;
        this.jobService     = jobService;
        this.paymentService = paymentService;
        this.badgeService   = badgeService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Submits a rating for a completed job.
     *
     * <p>Steps:
     * <ol>
     *   <li>Load the job and validate its status.</li>
     *   <li>Determine the caller's role (REQUESTER or WORKER) and the ratee UID.</li>
     *   <li>Reject if the caller is not a party to this job.</li>
     *   <li>Reject if a rating already exists for this party.</li>
     *   <li>Write the rating document to {@code ratings/{jobId}_{raterRole}}.</li>
     *   <li>If the Requester rated: update the Worker's rolling average.</li>
     *   <li>If both parties have now rated and the job is COMPLETE: release payment.</li>
     * </ol>
     *
     * @param jobId     Firestore job document ID
     * @param callerUid Firebase UID of the caller
     * @param req       rating payload
     * @return the persisted {@link Rating}
     * @throws ResponseStatusException  409 if the job is not in a rateable state, or a duplicate
     * @throws AccessDeniedException    if the caller is not the Requester or assigned Worker
     */
    public Rating submitRating(String jobId, String callerUid, RatingRequest req) {

        // 1. Load job and validate status.
        Job job = jobService.getJob(jobId);
        String status = job.getStatus();
        if (!RATEABLE_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ratings may only be submitted after the job is complete. "
                    + "Current status: " + status);
        }

        // 2. Determine raterRole and rateeUid.
        String raterRole;
        String rateeUid;
        if (callerUid.equals(job.getRequesterId())) {
            raterRole = "REQUESTER";
            rateeUid  = job.getWorkerId();
        } else if (callerUid.equals(job.getWorkerId())) {
            raterRole = "WORKER";
            rateeUid  = job.getRequesterId();
        } else {
            throw new AccessDeniedException("You are not a party to this job");
        }

        // 3. Check for duplicate.
        String ratingId = jobId + "_" + raterRole;
        try {
            DocumentSnapshot existing = firestore.collection(RATINGS_COLLECTION)
                    .document(ratingId).get().get();
            if (existing.exists()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "You have already submitted a rating for this job");
            }
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to check existing rating");
        }

        // 4. Build and write the rating document.
        Rating rating = new Rating();
        rating.setRatingId(ratingId);
        rating.setJobId(jobId);
        rating.setRaterUid(callerUid);
        rating.setRateeUid(rateeUid);
        rating.setRaterRole(raterRole);
        rating.setStars(req.getStars());
        rating.setReviewText(req.getReviewText());
        rating.setWouldRepeat(req.getWouldRepeat());
        rating.setCreatedAt(Timestamp.now());

        try {
            firestore.collection(RATINGS_COLLECTION).document(ratingId).set(rating).get();
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to save rating");
        }
        log.info("Rating saved: {} raterRole={} stars={}", ratingId, raterRole, req.getStars());

        // 5. Update the Worker's rolling average rating (only when Requester rates).
        if ("REQUESTER".equals(raterRole) && rateeUid != null) {
            updateWorkerRating(rateeUid, req.getStars());
        }

        // 6. If both parties have rated and the job is PENDING_APPROVAL, release payment now.
        if ("PENDING_APPROVAL".equals(status)) {
            checkAndRelease(jobId);
        }

        return rating;
    }

    /**
     * Returns all ratings for a given job (up to two documents: REQUESTER and WORKER).
     *
     * @param jobId Firestore job document ID
     * @return list of ratings; empty if no ratings have been submitted yet
     */
    public List<Rating> getRatingsForJob(String jobId) {
        try {
            QuerySnapshot snap = firestore.collection(RATINGS_COLLECTION)
                    .whereEqualTo("jobId", jobId)
                    .get().get();
            return snap.getDocuments().stream()
                    .map(doc -> doc.toObject(Rating.class))
                    .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to fetch ratings for job {}: {}", jobId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch ratings");
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Checks whether both the Requester and Worker rating documents exist.
     * If so, transitions the job to RELEASED and triggers the Stripe payout.
     *
     * <p>If the job is already RELEASED (e.g. the 4-hour auto-release fired just
     * before this check), the {@link InvalidTransitionException} is caught and
     * swallowed — the payment has already been handled.
     */
    private void checkAndRelease(String jobId) {
        String requesterRatingId = jobId + "_REQUESTER";
        String workerRatingId    = jobId + "_WORKER";

        try {
            DocumentSnapshot requesterRating = firestore.collection(RATINGS_COLLECTION)
                    .document(requesterRatingId).get().get();
            DocumentSnapshot workerRating    = firestore.collection(RATINGS_COLLECTION)
                    .document(workerRatingId).get().get();

            if (!requesterRating.exists() || !workerRating.exists()) {
                // Still waiting for the other party to rate.
                return;
            }

            // Both parties have rated — release payment immediately.
            log.info("Both parties rated job {} — triggering immediate release", jobId);
            try {
                jobService.transition(jobId, "RELEASED", "system", false);
                paymentService.releasePayment(jobId);
            } catch (InvalidTransitionException e) {
                // Auto-release already fired; job is already RELEASED — no-op.
                log.info("Job {} already released when mutual ratings confirmed", jobId);
            }

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.error("Failed to check mutual ratings for job {}: {}", jobId, e.getMessage(), e);
            // Non-fatal: the 4-hour auto-release will still fire.
        }
    }

    /**
     * Updates the Worker's rolling average rating in a Firestore transaction.
     *
     * <p>The average is computed as:
     * {@code newRating = (currentRating * currentCount + newStars) / (currentCount + 1)}
     *
     * <p>Per spec §3.1, the rating is displayed publicly only once the Worker has
     * 10+ completed jobs.  We still track it from the first rating so the value
     * is ready when that threshold is crossed.
     *
     * @param workerUid Firebase UID of the Worker whose profile to update
     * @param newStars  star value (1–5) from the new Requester rating
     */
    private void updateWorkerRating(String workerUid, int newStars) {
        try {
            DocumentReference userRef = firestore.collection("users").document(workerUid);

            firestore.runTransaction(tx -> {
                DocumentSnapshot snap = tx.get(userRef).get();
                if (!snap.exists()) {
                    log.warn("Worker {} not found — cannot update average rating", workerUid);
                    return null;
                }

                Double currentRating = snap.getDouble("worker.rating");
                Long   currentCount  = snap.getLong("worker.ratingCount");

                long   count      = (currentCount != null) ? currentCount : 0L;
                double sum        = (currentRating != null ? currentRating * count : 0.0)
                        + newStars;
                long   newCount   = count + 1;
                double newRating  = sum / newCount;

                // Round to 2 decimal places to avoid floating-point drift.
                double roundedRating = Math.round(newRating * 100.0) / 100.0;

                tx.update(userRef,
                        "worker.rating",      roundedRating,
                        "worker.ratingCount", (int) newCount);
                return null;
            }).get();

            log.debug("Worker {} average rating updated (new contribution: {} stars)", workerUid, newStars);

            // Re-evaluate badges that depend on rating (TOP_RATED, EXPERIENCED).
            badgeService.evaluateBadges(workerUid);

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            // Non-fatal: the rating document is already written; average will be corrected
            // on the next rating or via an admin reconciliation job.
            log.error("Failed to update average rating for worker {}: {}", workerUid, e.getMessage(), e);
        }
    }
}
