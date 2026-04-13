package com.yosnowmow.service;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.GeoPoint;
import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LocationType;
import com.yosnowmow.util.GeoUtils;
import com.yosnowmow.util.HashUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Server-side geocoding with a three-tier fallback chain (spec §10.1).
 *
 * <ol>
 *   <li><strong>Google Maps Geocoding API</strong> — accepts ROOFTOP or
 *       RANGE_INTERPOLATED results; key is never sent to the browser.</li>
 *   <li><strong>FSA centroid</strong> — extracts the 3-character Forward
 *       Sortation Area from the address and looks it up in the bundled
 *       {@link #FSA_CENTROIDS} table (30+ GTA/Ontario entries).</li>
 *   <li><strong>Failure</strong> — throws {@link GeocodingException} so the
 *       caller can surface a meaningful error to the user.</li>
 * </ol>
 *
 * Results are cached in Firestore {@code geocache/{cacheKey}} for 30 days.
 * Cache key = SHA-256 of the normalised address string.
 *
 * <strong>Security note:</strong> the Maps API key is injected from the
 * environment variable {@code MAPS_API_KEY} and is NEVER logged or returned
 * to clients.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    /** Firestore collection for geocode result caching. */
    private static final String GEOCACHE_COLLECTION = "geocache";

    /** How long a cached geocode result is considered fresh. */
    private static final int CACHE_TTL_DAYS = 30;

    /**
     * FSA → {lat, lng} centroid table for GTA and major Ontario cities.
     *
     * Sources: Canada Post FSA boundary centroids (public data).
     * Covers the primary launch zones and surrounding areas.
     * Extended as new zones are activated.
     */
    private static final Map<String, double[]> FSA_CENTROIDS = new HashMap<>();

    static {
        // ── Toronto core ───────────────────────────────────────────────────
        FSA_CENTROIDS.put("M5V", new double[]{43.6426,  -79.3998}); // Downtown West
        FSA_CENTROIDS.put("M5G", new double[]{43.6579,  -79.3873}); // Hospital District
        FSA_CENTROIDS.put("M4Y", new double[]{43.6689,  -79.3872}); // Church-Wellesley
        FSA_CENTROIDS.put("M5S", new double[]{43.6629,  -79.3957}); // Annex
        FSA_CENTROIDS.put("M6J", new double[]{43.6494,  -79.4220}); // Trinity-Bellwoods
        FSA_CENTROIDS.put("M6H", new double[]{43.6598,  -79.4416}); // Dufferin Grove
        FSA_CENTROIDS.put("M6K", new double[]{43.6384,  -79.4316}); // Parkdale
        // ── East Toronto ─────────────────────────────────────────────────
        FSA_CENTROIDS.put("M4J", new double[]{43.6797,  -79.3397}); // East York
        FSA_CENTROIDS.put("M4E", new double[]{43.6773,  -79.2993}); // East End
        FSA_CENTROIDS.put("M1A", new double[]{43.7876,  -79.1785}); // Scarborough East
        FSA_CENTROIDS.put("M1B", new double[]{43.8065,  -79.1939}); // Malvern
        FSA_CENTROIDS.put("M1C", new double[]{43.7846,  -79.1584}); // Rouge Hill
        FSA_CENTROIDS.put("M1P", new double[]{43.7578,  -79.2742}); // Scarborough Town Centre
        // ── North York ────────────────────────────────────────────────────
        FSA_CENTROIDS.put("M2N", new double[]{43.7615,  -79.4083}); // Willowdale
        FSA_CENTROIDS.put("M3A", new double[]{43.7505,  -79.3326}); // Parkwoods
        FSA_CENTROIDS.put("M2R", new double[]{43.7817,  -79.4474}); // Bathurst Manor
        FSA_CENTROIDS.put("M6A", new double[]{43.7197,  -79.4488}); // Lawrence Heights
        // ── Etobicoke ─────────────────────────────────────────────────────
        FSA_CENTROIDS.put("M9A", new double[]{43.6666,  -79.5148}); // Islington-City Centre West
        FSA_CENTROIDS.put("M8Y", new double[]{43.6326,  -79.4836}); // Mimico
        FSA_CENTROIDS.put("M9V", new double[]{43.7453,  -79.5883}); // Rexdale
        FSA_CENTROIDS.put("M9W", new double[]{43.7067,  -79.5953}); // Humberwood
        // ── York / Weston ─────────────────────────────────────────────────
        FSA_CENTROIDS.put("M6E", new double[]{43.6917,  -79.4604}); // Caledonia-Fairbank
        FSA_CENTROIDS.put("M9N", new double[]{43.7062,  -79.5165}); // Weston
        // ── Mississauga ───────────────────────────────────────────────────
        FSA_CENTROIDS.put("L4W", new double[]{43.6274,  -79.6318}); // Mississauga Centre
        FSA_CENTROIDS.put("L5A", new double[]{43.5890,  -79.6230}); // Mississauga South
        FSA_CENTROIDS.put("L5N", new double[]{43.5699,  -79.7480}); // Streetsville
        FSA_CENTROIDS.put("L5B", new double[]{43.5801,  -79.6395}); // Cooksville
        // ── Brampton ──────────────────────────────────────────────────────
        FSA_CENTROIDS.put("L6P", new double[]{43.7530,  -79.7153}); // Brampton East
        FSA_CENTROIDS.put("L6T", new double[]{43.6994,  -79.7196}); // Bramalea
        FSA_CENTROIDS.put("L6X", new double[]{43.6922,  -79.7596}); // Brampton West
        // ── Markham / Richmond Hill ───────────────────────────────────────
        FSA_CENTROIDS.put("L3R", new double[]{43.8462,  -79.3524}); // Markham Centre
        FSA_CENTROIDS.put("L4B", new double[]{43.8500,  -79.4187}); // Richmond Hill South
        FSA_CENTROIDS.put("L4C", new double[]{43.8784,  -79.4339}); // Richmond Hill North
        // ── Vaughan / Woodbridge ──────────────────────────────────────────
        FSA_CENTROIDS.put("L4H", new double[]{43.7964,  -79.5928}); // Woodbridge
        FSA_CENTROIDS.put("L4K", new double[]{43.7953,  -79.5230}); // Vaughan South
        // ── Hamilton ──────────────────────────────────────────────────────
        FSA_CENTROIDS.put("L8P", new double[]{43.2601,  -79.8808}); // Hamilton West
        FSA_CENTROIDS.put("L8N", new double[]{43.2432,  -79.8476}); // Hamilton East
        // ── Ottawa ────────────────────────────────────────────────────────
        FSA_CENTROIDS.put("K1A", new double[]{45.4215,  -75.6972}); // Ottawa Centre
        FSA_CENTROIDS.put("K2P", new double[]{45.4126,  -75.7023}); // Centretown
        FSA_CENTROIDS.put("K1Y", new double[]{45.4023,  -75.7433}); // Westboro
        // ── Other major Ontario cities ────────────────────────────────────
        FSA_CENTROIDS.put("N2J", new double[]{43.4668,  -80.5164}); // Waterloo
        FSA_CENTROIDS.put("N6A", new double[]{42.9849,  -81.2453}); // London
        FSA_CENTROIDS.put("L2G", new double[]{43.0896,  -79.0849}); // Niagara Falls
        FSA_CENTROIDS.put("P3E", new double[]{46.4902,  -80.9970}); // Sudbury
    }

    // ── Result type ───────────────────────────────────────────────────────────

    /**
     * The result of a geocode operation — a GeoPoint and the method that
     * produced it (used to populate {@code addressGeocodeMethod} on the profile).
     */
    public record GeocodeResult(GeoPoint coords, String method) {}

    // ── Exception ─────────────────────────────────────────────────────────────

    /** Thrown when all fallback tiers are exhausted without resolving coordinates. */
    public static class GeocodingException extends RuntimeException {
        public GeocodingException(String address) {
            super("Could not geocode address: " + address);
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final Firestore firestore;
    private final GeoApiContext geoApiContext;

    public GeocodingService(
            Firestore firestore,
            @Value("${yosnowmow.maps.api-key}") String mapsApiKey) {

        this.firestore = firestore;
        this.geoApiContext = new GeoApiContext.Builder()
                .apiKey(mapsApiKey)
                .build();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Geocodes the given address string using the fallback chain described in
     * spec §10.1.
     *
     * Results are cached in Firestore for {@value #CACHE_TTL_DAYS} days.
     *
     * @param address the full address text (e.g. "456 Oak Ave, Etobicoke, ON M8Y 2B3")
     * @return a {@link GeocodeResult} containing the GeoPoint and the method used
     * @throws GeocodingException if all fallback tiers fail
     */
    public GeocodeResult geocode(String address) {
        String normalised = GeoUtils.normalizeAddress(address);
        String cacheKey   = HashUtils.sha256(normalised);

        // 1. Try the cache first
        GeocodeResult cached = checkCache(cacheKey);
        if (cached != null) {
            log.debug("Geocode cache hit for key {}", cacheKey);
            return cached;
        }

        // 2. Try Google Maps API
        GeocodeResult result = tryGoogleMaps(address, normalised);

        // 3. FSA centroid fallback
        if (result == null) {
            result = tryFsaCentroid(address);
        }

        // 4. All tiers exhausted
        if (result == null) {
            log.warn("Geocoding failed for address: {}", normalised);
            throw new GeocodingException(address);
        }

        // 5. Cache the result
        writeCache(cacheKey, result);
        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Checks the Firestore geocache for a fresh (< 30-day-old) result.
     *
     * @return a GeocodeResult if found and fresh, or null otherwise
     */
    private GeocodeResult checkCache(String cacheKey) {
        try {
            DocumentSnapshot snap = firestore
                    .collection(GEOCACHE_COLLECTION)
                    .document(cacheKey)
                    .get().get();

            if (!snap.exists()) {
                return null;
            }

            // Check TTL
            Timestamp cachedAt = snap.getTimestamp("cachedAt");
            if (cachedAt == null) {
                return null;
            }
            Instant cacheTime = Instant.ofEpochSecond(cachedAt.getSeconds());
            if (cacheTime.isBefore(Instant.now().minus(CACHE_TTL_DAYS, ChronoUnit.DAYS))) {
                return null; // Expired
            }

            Double lat    = snap.getDouble("lat");
            Double lng    = snap.getDouble("lng");
            String method = snap.getString("method");

            if (lat == null || lng == null) {
                return null;
            }
            return new GeocodeResult(new GeoPoint(lat, lng), method);

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.warn("Cache read failed for key {}: {}", cacheKey, e.getMessage());
            return null; // Non-fatal — proceed to live geocode
        }
    }

    /**
     * Calls the Google Maps Geocoding API.
     *
     * Accepts ROOFTOP (exact match) and RANGE_INTERPOLATED (interpolated address)
     * results per spec §10.1.  GEOMETRIC_CENTER and APPROXIMATE are rejected to
     * avoid inaccurate centroids being used for distance pricing.
     *
     * Returns null on any exception — callers fall through to the FSA fallback.
     *
     * @param address    the original address string passed to the API
     * @param normalised the normalised address (for logging only — key never logged)
     */
    private GeocodeResult tryGoogleMaps(String address, String normalised) {
        try {
            GeocodingResult[] results = GeocodingApi
                    .geocode(geoApiContext, address)
                    .await();

            if (results == null || results.length == 0) {
                log.debug("Google Maps returned no results for: {}", normalised);
                return null;
            }

            for (GeocodingResult r : results) {
                LocationType type = r.geometry.locationType;
                if (type == LocationType.ROOFTOP || type == LocationType.RANGE_INTERPOLATED) {
                    double lat = r.geometry.location.lat;
                    double lng = r.geometry.location.lng;
                    log.debug("Google Maps geocoded {} → {}, {} ({})", normalised, lat, lng, type);
                    return new GeocodeResult(new GeoPoint(lat, lng), "google_maps");
                }
            }

            log.debug("Google Maps result quality too low for: {}", normalised);
            return null;

        } catch (Exception e) {
            // Catch-all: API timeout, quota exceeded, network error, etc.
            // Never propagate as 500 — fall through to FSA fallback.
            log.warn("Google Maps geocoding error for [redacted]: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Looks up the FSA centroid from the address string.
     *
     * Tries to extract a postal code pattern from the address; if found, looks
     * up the FSA in {@link #FSA_CENTROIDS}.
     *
     * @return a GeocodeResult with method "postal_code", or null if no match
     */
    private GeocodeResult tryFsaCentroid(String address) {
        String fsa = GeoUtils.extractFSAFromAddress(address);
        if (fsa != null) {
            double[] coords = FSA_CENTROIDS.get(fsa);
            if (coords != null) {
                log.debug("FSA centroid fallback used: FSA={}", fsa);
                return new GeocodeResult(new GeoPoint(coords[0], coords[1]), "postal_code");
            }
            log.debug("FSA not found in centroid table: {}", fsa);
        } else {
            log.debug("No postal code found in address for FSA fallback");
        }
        return null;
    }

    /**
     * Writes a geocode result to the Firestore cache.
     * Non-fatal on failure — the system will just re-geocode next time.
     */
    private void writeCache(String cacheKey, GeocodeResult result) {
        try {
            Map<String, Object> doc = new HashMap<>();
            doc.put("lat",      result.coords().getLatitude());
            doc.put("lng",      result.coords().getLongitude());
            doc.put("method",   result.method());
            doc.put("cachedAt", Timestamp.now());

            firestore.collection(GEOCACHE_COLLECTION)
                     .document(cacheKey)
                     .set(doc)
                     .get();

        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            log.warn("Failed to write geocache entry {}: {}", cacheKey, e.getMessage());
        }
    }
}
