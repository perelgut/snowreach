package com.yosnowmow.model;

import com.google.cloud.Timestamp;

/**
 * Represents a rating/review document in the Firestore {@code ratings} collection.
 *
 * <p>Document ID convention: {@code {jobId}_{raterRole}}
 * (e.g. {@code abc123_REQUESTER} for the Requester's rating of the Worker).
 *
 * <p>This scheme gives each job at most two rating documents — one per party —
 * and makes duplicate detection a single document existence check.
 *
 * <p>Schema per spec §3.3:
 * <ul>
 *   <li>{@code raterRole} = "REQUESTER" (Requester rates the Worker's service quality) or
 *       "WORKER" (Worker rates the Requester's behaviour).</li>
 *   <li>{@code stars} = integer 1–5.</li>
 *   <li>{@code reviewText} = free-form text, max 500 chars.</li>
 *   <li>{@code wouldRepeat} = boolean.</li>
 * </ul>
 */
public class Rating {

    /** Firestore document ID: {@code {jobId}_{raterRole}}. */
    private String ratingId;

    private String jobId;

    /** Firebase UID of the person submitting the rating. */
    private String raterUid;

    /** Firebase UID of the person being rated. */
    private String rateeUid;

    /**
     * Role of the person submitting this rating.
     * One of: "REQUESTER" | "WORKER"
     */
    private String raterRole;

    /** 1–5 stars. */
    private int stars;

    /** Optional free-form review text (max 500 characters). */
    private String reviewText;

    /** True if the rater would use / work with this person again. */
    private boolean wouldRepeat;

    private Timestamp createdAt;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Required by Firestore deserialisation. */
    public Rating() {}

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getRatingId() { return ratingId; }
    public void setRatingId(String ratingId) { this.ratingId = ratingId; }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }

    public String getRaterUid() { return raterUid; }
    public void setRaterUid(String raterUid) { this.raterUid = raterUid; }

    public String getRateeUid() { return rateeUid; }
    public void setRateeUid(String rateeUid) { this.rateeUid = rateeUid; }

    public String getRaterRole() { return raterRole; }
    public void setRaterRole(String raterRole) { this.raterRole = raterRole; }

    public int getStars() { return stars; }
    public void setStars(int stars) { this.stars = stars; }

    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) { this.reviewText = reviewText; }

    public boolean isWouldRepeat() { return wouldRepeat; }
    public void setWouldRepeat(boolean wouldRepeat) { this.wouldRepeat = wouldRepeat; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
