package com.yosnowmow;

import com.yosnowmow.util.GeoUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GeoUtils — Haversine distance formula and address helpers.
 *
 * Coordinates used in distance tests are taken directly from the seed
 * worker profiles (seed-emulator.js) so results can be cross-checked
 * against Google Maps at any time.
 *
 *   Alex Moreau   (Worker)   43.4675, -79.6877  — Oakville, ON
 *   Jordan Tremblay (Worker) 43.3255, -79.7990  — Burlington, ON
 *
 * Requester sample addresses:
 *   Downtown Oakville QEW/Kerr area  43.4489, -79.6960
 *   Mississauga City Centre          43.5890, -79.6441
 */
class GeoUtilsTest {

    // ── Seed worker coordinates ───────────────────────────────────────────────

    /** Alex Moreau base — Oakville, ON (from seed-emulator.js) */
    private static final double ALEX_LAT  =  43.4675;
    private static final double ALEX_LON  = -79.6877;

    /** Jordan Tremblay base — Burlington, ON (from seed-emulator.js) */
    private static final double JORDAN_LAT =  43.3255;
    private static final double JORDAN_LON = -79.7990;

    // ── Sample requester job locations ────────────────────────────────────────

    /** Same point as Alex — distance must be 0 */
    private static final double SAME_AS_ALEX_LAT = ALEX_LAT;
    private static final double SAME_AS_ALEX_LON = ALEX_LON;

    /** ~2 km south of Alex's base — should be well within his 10 km radius */
    private static final double NEARBY_OAKVILLE_LAT =  43.4489;
    private static final double NEARBY_OAKVILLE_LON = -79.6960;

    /** Mississauga City Centre — ~14 km from Alex, outside his 10 km radius */
    private static final double MISSISSAUGA_LAT =  43.5890;
    private static final double MISSISSAUGA_LON = -79.6441;

    // ── Haversine distance tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Distance from a point to itself is zero")
    void samePoint_returnsZero() {
        double dist = GeoUtils.haversineDistanceKm(ALEX_LAT, ALEX_LON,
                                                    SAME_AS_ALEX_LAT, SAME_AS_ALEX_LON);
        assertEquals(0.0, dist, 1e-9,
                "Distance from a point to itself must be exactly 0 km");
    }

    @Test
    @DisplayName("Alex (Oakville) to nearby Oakville job — approx 2.1 km")
    void alexToNearbyOakville_withinRadius() {
        double dist = GeoUtils.haversineDistanceKm(ALEX_LAT, ALEX_LON,
                                                    NEARBY_OAKVILLE_LAT, NEARBY_OAKVILLE_LON);

        System.out.printf(
            "[GeoUtils] Alex (Oakville) → nearby Oakville job: %.2f km%n", dist);

        // Expect roughly 2.1 km; allow ±0.3 km for coordinate imprecision
        assertEquals(2.1, dist, 0.3,
                "Expected ~2.1 km between Alex's base and nearby Oakville job");

        // Must be within Alex's 10 km service radius
        assertTrue(dist <= 10.0,
                "Job should be within Alex's 10 km service radius");
    }

    @Test
    @DisplayName("Alex (Oakville) to Mississauga City Centre — approx 14 km, outside radius")
    void alexToMississauga_outsideRadius() {
        double dist = GeoUtils.haversineDistanceKm(ALEX_LAT, ALEX_LON,
                                                    MISSISSAUGA_LAT, MISSISSAUGA_LON);

        System.out.printf(
            "[GeoUtils] Alex (Oakville) → Mississauga City Centre: %.2f km%n", dist);

        // Expect roughly 14 km; allow ±1 km
        assertEquals(14.0, dist, 1.0,
                "Expected ~14 km between Alex's base and Mississauga City Centre");

        // Must exceed Alex's 10 km service radius (job should not be matched to him)
        assertTrue(dist > 10.0,
                "Mississauga job should be outside Alex's 10 km service radius");
    }

    @Test
    @DisplayName("Alex (Oakville) to Jordan (Burlington) — approx 17-18 km")
    void workerToWorker_oakvilleToBurlington() {
        double dist = GeoUtils.haversineDistanceKm(ALEX_LAT, ALEX_LON,
                                                    JORDAN_LAT, JORDAN_LON);

        System.out.printf(
            "[GeoUtils] Alex (Oakville) → Jordan (Burlington): %.2f km%n", dist);

        // Oakville to Burlington is ~17-18 km via straight line
        assertEquals(17.5, dist, 1.5,
                "Expected ~17-18 km between Oakville and Burlington");
    }

    @Test
    @DisplayName("Distance is symmetric — A→B equals B→A")
    void distance_isSymmetric() {
        double aToB = GeoUtils.haversineDistanceKm(ALEX_LAT, ALEX_LON,
                                                    JORDAN_LAT, JORDAN_LON);
        double bToA = GeoUtils.haversineDistanceKm(JORDAN_LAT, JORDAN_LON,
                                                    ALEX_LAT, ALEX_LON);

        assertEquals(aToB, bToA, 1e-9,
                "Haversine distance must be symmetric (A→B == B→A)");
    }

    // ── Tier price selection helper ───────────────────────────────────────────

    @Test
    @DisplayName("Nearby Oakville job falls in Alex's first tier (≤5 km → $45.00)")
    void nearbyJob_selectsFirstTier() {
        double dist = GeoUtils.haversineDistanceKm(ALEX_LAT, ALEX_LON,
                                                    NEARBY_OAKVILLE_LAT, NEARBY_OAKVILLE_LON);

        // Alex's seed tiers: [0–5 km → $45, 5–10 km → $60]
        long priceCents = dist <= 5.0 ? 4500L : 6000L;

        System.out.printf(
            "[GeoUtils] Nearby job at %.2f km → tier price: $%.2f%n",
            dist, priceCents / 100.0);

        assertEquals(4500L, priceCents,
                "A job ~2 km away should fall in the ≤5 km tier at $45.00");
    }

    // ── Address normalisation tests ───────────────────────────────────────────

    @Test
    @DisplayName("normalizeAddress lowercases and collapses whitespace")
    void normalizeAddress_basic() {
        String result = GeoUtils.normalizeAddress("  456 Oak Ave,  Etobicoke, ON  M8Y 2B3  ");
        assertEquals("456 oak ave, etobicoke, on m8y 2b3", result);
    }

    @Test
    @DisplayName("normalizeAddress returns empty string for null/blank input")
    void normalizeAddress_nullAndBlank() {
        assertEquals("", GeoUtils.normalizeAddress(null));
        assertEquals("", GeoUtils.normalizeAddress("   "));
        assertEquals("", GeoUtils.normalizeAddress(""));
    }

    // ── FSA extraction tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("extractFSA handles spaced and compact postal codes")
    void extractFSA_formats() {
        assertEquals("M8Y", GeoUtils.extractFSA("M8Y 2B3"));
        assertEquals("M8Y", GeoUtils.extractFSA("M8Y2B3"));
        assertEquals("L6J", GeoUtils.extractFSA("L6J 2V3"));   // Alex's postal code
    }

    @Test
    @DisplayName("extractFSA returns null for null/blank/short input")
    void extractFSA_nullAndBlank() {
        assertNull(GeoUtils.extractFSA(null));
        assertNull(GeoUtils.extractFSA(""));
        assertNull(GeoUtils.extractFSA("M8"));
    }

    @Test
    @DisplayName("extractFSAFromAddress finds postal code embedded in a full address")
    void extractFSAFromAddress_fullAddress() {
        assertEquals("L6J",
            GeoUtils.extractFSAFromAddress("123 Maple Ave, Oakville, ON L6J 2V3"));
        assertEquals("L7R",
            GeoUtils.extractFSAFromAddress("456 Pine St, Burlington, ON L7R 1A1"));
        assertEquals("M8Y",
            GeoUtils.extractFSAFromAddress("456 Oak Ave, Etobicoke, ON M8Y 2B3"));
    }

    @Test
    @DisplayName("extractFSAFromAddress returns null when no postal code in string")
    void extractFSAFromAddress_noPostalCode() {
        assertNull(GeoUtils.extractFSAFromAddress("123 Main Street, Toronto"));
        assertNull(GeoUtils.extractFSAFromAddress(null));
    }
}
