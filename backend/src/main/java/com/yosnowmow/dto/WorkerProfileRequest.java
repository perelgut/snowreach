package com.yosnowmow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/users/me/worker} (activate Worker role)
 * and {@code PATCH /api/users/me/worker} (update Worker profile).
 *
 * For PATCH, only non-null fields are applied.  Required fields are still
 * validated when present, but {@code @NotNull} is only applied on POST
 * (enforcement handled in WorkerService based on whether the profile exists).
 *
 * Note: The {@code baseAddress} is the Worker's home base — it is geocoded
 * server-side (GeocodingService, P1-07) and never returned in Requester contexts.
 */
public class WorkerProfileRequest {

    /**
     * Worker type.
     * Must be "personal" or "dispatcher".
     */
    @Pattern(regexp = "personal|dispatcher",
             message = "designation must be 'personal' or 'dispatcher'")
    private String designation;

    /**
     * Worker's base address full text (e.g. "456 Oak Ave, Etobicoke, ON M8Y 2B3").
     * Geocoded to coordinates server-side in P1-07.
     */
    @NotBlank(message = "baseAddress.fullText is required")
    private String baseAddressFullText;

    /**
     * Maximum distance in km the Worker is willing to travel.
     * Discrete UI options: 0.25 (250 m), 1, 5, 10, 25, 50 km.
     */
    @DecimalMin(value = "0.1", message = "serviceRadiusKm must be at least 0.1 km")
    @DecimalMax(value = "50.0", message = "serviceRadiusKm must not exceed 50 km")
    private Double serviceRadiusKm;

    /**
     * Whether the Worker opts in to receiving requests slightly outside their radius.
     */
    private Boolean bufferOptIn;

    /**
     * Distance-based pricing tiers — 1 to 3 entries, ordered by maxDistanceKm.
     * The outermost tier's maxDistanceKm must equal serviceRadiusKm.
     */
    @Size(min = 1, max = 3, message = "tiers must have 1 to 3 entries")
    @Valid
    private List<TierDto> tiers;

    /**
     * Whether the Worker is registered for HST (Ontario).
     * When true, hstBusinessNumber is required.
     */
    private Boolean hstRegistered;

    /**
     * HST business number (e.g. "123456789RT0001").
     * Required when hstRegistered is true; ignored otherwise.
     */
    private String hstBusinessNumber;

    /**
     * UID of the Worker who referred this Worker, if any.
     * Recorded at activation only; ignored on subsequent patches.
     */
    private String referredByUserId;

    /**
     * Availability status override.
     * Accepted on PATCH only: "available" | "unavailable"
     * Workers cannot directly set "busy" — that is set by the job dispatch system.
     */
    @Pattern(regexp = "available|unavailable",
             message = "status must be 'available' or 'unavailable'")
    private String status;

    // ── Nested DTO ────────────────────────────────────────────────────────────

    /**
     * A single pricing tier within the request payload.
     * Validated independently via {@code @Valid} on the list.
     */
    public static class TierDto {

        @DecimalMin(value = "0.1", message = "maxDistanceKm must be positive")
        @NotNull(message = "maxDistanceKm is required")
        private Double maxDistanceKm;

        @DecimalMin(value = "1.0", message = "priceCAD must be at least $1")
        @NotNull(message = "priceCAD is required")
        private Double priceCAD;

        public Double getMaxDistanceKm() { return maxDistanceKm; }
        public void setMaxDistanceKm(Double maxDistanceKm) { this.maxDistanceKm = maxDistanceKm; }

        public Double getPriceCAD() { return priceCAD; }
        public void setPriceCAD(Double priceCAD) { this.priceCAD = priceCAD; }
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }

    public String getBaseAddressFullText() { return baseAddressFullText; }
    public void setBaseAddressFullText(String baseAddressFullText) { this.baseAddressFullText = baseAddressFullText; }

    public Double getServiceRadiusKm() { return serviceRadiusKm; }
    public void setServiceRadiusKm(Double serviceRadiusKm) { this.serviceRadiusKm = serviceRadiusKm; }

    public Boolean getBufferOptIn() { return bufferOptIn; }
    public void setBufferOptIn(Boolean bufferOptIn) { this.bufferOptIn = bufferOptIn; }

    public List<TierDto> getTiers() { return tiers; }
    public void setTiers(List<TierDto> tiers) { this.tiers = tiers; }

    public Boolean getHstRegistered() { return hstRegistered; }
    public void setHstRegistered(Boolean hstRegistered) { this.hstRegistered = hstRegistered; }

    public String getHstBusinessNumber() { return hstBusinessNumber; }
    public void setHstBusinessNumber(String hstBusinessNumber) { this.hstBusinessNumber = hstBusinessNumber; }

    public String getReferredByUserId() { return referredByUserId; }
    public void setReferredByUserId(String referredByUserId) { this.referredByUserId = referredByUserId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
