package com.yosnowmow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/jobs/{jobId}/rating}.
 *
 * Both parties (Requester and Worker) use the same DTO.
 */
public class RatingRequest {

    /** 1–5 stars (required). */
    @NotNull
    @Min(1)
    @Max(5)
    private Integer stars;

    /** Optional review text (max 500 characters). */
    @Size(max = 500)
    private String reviewText;

    /** Whether the rater would use / work with this person again (required). */
    @NotNull
    private Boolean wouldRepeat;

    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }

    public String getReviewText() { return reviewText; }
    public void setReviewText(String reviewText) { this.reviewText = reviewText; }

    public Boolean getWouldRepeat() { return wouldRepeat; }
    public void setWouldRepeat(Boolean wouldRepeat) { this.wouldRepeat = wouldRepeat; }
}
