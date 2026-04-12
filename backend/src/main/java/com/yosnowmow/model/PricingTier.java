package com.yosnowmow.model;

/**
 * A single distance-based pricing tier in a Worker's profile.
 *
 * Workers define up to 3 tiers, ordered by increasing distance.
 * The applicable price for a job is determined by the tier whose
 * {@code maxDistanceKm} is greater than or equal to the job distance.
 *
 * Matches the Firestore schema from spec §3.1 ({@code worker.tiers}).
 */
public class PricingTier {

    /** Outer boundary of this tier in kilometres (e.g. 3.0, 7.0, 10.0). */
    private double maxDistanceKm;

    /** Price in Canadian dollars (e.g. 40.0) — displayed to the Requester as-is. */
    private double priceCAD;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Required by Firestore deserialisation. */
    public PricingTier() {}

    public PricingTier(double maxDistanceKm, double priceCAD) {
        this.maxDistanceKm = maxDistanceKm;
        this.priceCAD = priceCAD;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public double getMaxDistanceKm() { return maxDistanceKm; }
    public void setMaxDistanceKm(double maxDistanceKm) { this.maxDistanceKm = maxDistanceKm; }

    public double getPriceCAD() { return priceCAD; }
    public void setPriceCAD(double priceCAD) { this.priceCAD = priceCAD; }
}
