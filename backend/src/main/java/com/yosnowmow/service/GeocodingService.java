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
        // ── Scarborough ───────────────────────────────────────────────────
        FSA_CENTROIDS.put("M1A", new double[]{43.7876, -79.1785}); // Scarborough East
        FSA_CENTROIDS.put("M1B", new double[]{43.8065, -79.1939}); // Malvern
        FSA_CENTROIDS.put("M1C", new double[]{43.7846, -79.1584}); // Rouge Hill
        FSA_CENTROIDS.put("M1E", new double[]{43.7652, -79.1887}); // West Hill
        FSA_CENTROIDS.put("M1G", new double[]{43.7705, -79.2168}); // Woburn
        FSA_CENTROIDS.put("M1H", new double[]{43.7731, -79.2394}); // Cedarbrae
        FSA_CENTROIDS.put("M1J", new double[]{43.7448, -79.2322}); // Scarborough Village
        FSA_CENTROIDS.put("M1K", new double[]{43.7277, -79.2659}); // Kennedy Park
        FSA_CENTROIDS.put("M1L", new double[]{43.7113, -79.2846}); // Clairlea
        FSA_CENTROIDS.put("M1M", new double[]{43.7183, -79.2468}); // Cliffside
        FSA_CENTROIDS.put("M1N", new double[]{43.7096, -79.2649}); // Birchcliffe
        FSA_CENTROIDS.put("M1P", new double[]{43.7578, -79.2742}); // Scarborough Town Centre
        FSA_CENTROIDS.put("M1R", new double[]{43.7506, -79.3006}); // Wexford
        FSA_CENTROIDS.put("M1S", new double[]{43.7944, -79.2690}); // Agincourt
        FSA_CENTROIDS.put("M1T", new double[]{43.7818, -79.3059}); // Tam O'Shanter
        FSA_CENTROIDS.put("M1V", new double[]{43.8153, -79.2955}); // Milliken
        FSA_CENTROIDS.put("M1W", new double[]{43.7989, -79.3307}); // Steeles East
        FSA_CENTROIDS.put("M1X", new double[]{43.8344, -79.2179}); // Upper Rouge
        // ── North York ────────────────────────────────────────────────────
        FSA_CENTROIDS.put("M2H", new double[]{43.8035, -79.3632}); // Hillcrest Village
        FSA_CENTROIDS.put("M2J", new double[]{43.7778, -79.3509}); // Fairview
        FSA_CENTROIDS.put("M2K", new double[]{43.7864, -79.4055}); // Bayview Village
        FSA_CENTROIDS.put("M2L", new double[]{43.7578, -79.3760}); // York Mills
        FSA_CENTROIDS.put("M2M", new double[]{43.7893, -79.3988}); // Willowdale East
        FSA_CENTROIDS.put("M2N", new double[]{43.7615, -79.4083}); // Willowdale West
        FSA_CENTROIDS.put("M2P", new double[]{43.7478, -79.4066}); // York Mills South
        FSA_CENTROIDS.put("M2R", new double[]{43.7817, -79.4474}); // Bathurst Manor
        FSA_CENTROIDS.put("M3A", new double[]{43.7505, -79.3326}); // Parkwoods
        FSA_CENTROIDS.put("M3B", new double[]{43.7484, -79.3560}); // Don Mills North
        FSA_CENTROIDS.put("M3C", new double[]{43.7263, -79.3407}); // Don Mills South
        FSA_CENTROIDS.put("M3H", new double[]{43.7538, -79.4437}); // Bathurst Manor North
        FSA_CENTROIDS.put("M3J", new double[]{43.7609, -79.4883}); // Northwood Park
        FSA_CENTROIDS.put("M3K", new double[]{43.7374, -79.4652}); // Downsview
        FSA_CENTROIDS.put("M3L", new double[]{43.7351, -79.4887}); // Downsview West
        FSA_CENTROIDS.put("M3M", new double[]{43.7283, -79.5106}); // Downsview North
        FSA_CENTROIDS.put("M3N", new double[]{43.7616, -79.5201}); // Jane and Finch
        // ── East Toronto / East York ──────────────────────────────────────
        FSA_CENTROIDS.put("M4A", new double[]{43.7268, -79.3133}); // Victoria Village
        FSA_CENTROIDS.put("M4B", new double[]{43.7089, -79.2972}); // Parkview Hill
        FSA_CENTROIDS.put("M4C", new double[]{43.6979, -79.3178}); // Woodbine Heights
        FSA_CENTROIDS.put("M4E", new double[]{43.6773, -79.2993}); // East End
        FSA_CENTROIDS.put("M4G", new double[]{43.7072, -79.3635}); // Leaside
        FSA_CENTROIDS.put("M4H", new double[]{43.7039, -79.3426}); // Thorncliffe Park
        FSA_CENTROIDS.put("M4J", new double[]{43.6797, -79.3397}); // East York
        FSA_CENTROIDS.put("M4K", new double[]{43.6814, -79.3518}); // Danforth
        FSA_CENTROIDS.put("M4L", new double[]{43.6690, -79.3205}); // India Bazaar
        FSA_CENTROIDS.put("M4M", new double[]{43.6596, -79.3360}); // Studio District
        FSA_CENTROIDS.put("M4N", new double[]{43.7286, -79.3941}); // Lawrence Park
        FSA_CENTROIDS.put("M4P", new double[]{43.7129, -79.3876}); // Davisville North
        FSA_CENTROIDS.put("M4R", new double[]{43.7190, -79.4040}); // North Toronto
        FSA_CENTROIDS.put("M4S", new double[]{43.7059, -79.3976}); // Davisville
        FSA_CENTROIDS.put("M4T", new double[]{43.6915, -79.3900}); // Moore Park
        FSA_CENTROIDS.put("M4V", new double[]{43.6866, -79.4004}); // Summerhill
        FSA_CENTROIDS.put("M4W", new double[]{43.6784, -79.3789}); // Rosedale
        FSA_CENTROIDS.put("M4X", new double[]{43.6677, -79.3674}); // Cabbagetown
        FSA_CENTROIDS.put("M4Y", new double[]{43.6689, -79.3872}); // Church-Wellesley
        // ── Downtown Toronto ──────────────────────────────────────────────
        FSA_CENTROIDS.put("M5A", new double[]{43.6544, -79.3631}); // Regent Park / Harbourfront
        FSA_CENTROIDS.put("M5B", new double[]{43.6576, -79.3779}); // Garden District
        FSA_CENTROIDS.put("M5C", new double[]{43.6514, -79.3761}); // St. James Town
        FSA_CENTROIDS.put("M5E", new double[]{43.6488, -79.3732}); // Financial District
        FSA_CENTROIDS.put("M5G", new double[]{43.6579, -79.3873}); // Hospital District
        FSA_CENTROIDS.put("M5H", new double[]{43.6481, -79.3834}); // Bay Street Corridor
        FSA_CENTROIDS.put("M5J", new double[]{43.6407, -79.3811}); // Harbourfront East
        FSA_CENTROIDS.put("M5K", new double[]{43.6479, -79.3840}); // Design Exchange
        FSA_CENTROIDS.put("M5L", new double[]{43.6489, -79.3810}); // Commerce Court
        FSA_CENTROIDS.put("M5M", new double[]{43.7330, -79.4170}); // Bedford Park
        FSA_CENTROIDS.put("M5N", new double[]{43.7117, -79.4193}); // Roselawn
        FSA_CENTROIDS.put("M5P", new double[]{43.6946, -79.4113}); // Forest Hill North
        FSA_CENTROIDS.put("M5R", new double[]{43.6795, -79.4100}); // The Annex
        FSA_CENTROIDS.put("M5S", new double[]{43.6629, -79.3957}); // Annex South
        FSA_CENTROIDS.put("M5T", new double[]{43.6527, -79.4022}); // Kensington Market
        FSA_CENTROIDS.put("M5V", new double[]{43.6426, -79.3998}); // Downtown West
        FSA_CENTROIDS.put("M5W", new double[]{43.6433, -79.3820}); // Stn A
        FSA_CENTROIDS.put("M5X", new double[]{43.6487, -79.3822}); // First Canadian Place
        // ── West Toronto / York ───────────────────────────────────────────
        FSA_CENTROIDS.put("M6A", new double[]{43.7197, -79.4488}); // Lawrence Heights
        FSA_CENTROIDS.put("M6B", new double[]{43.7100, -79.4511}); // Glencairn
        FSA_CENTROIDS.put("M6C", new double[]{43.6989, -79.4303}); // Humewood-Cedarvale
        FSA_CENTROIDS.put("M6E", new double[]{43.6917, -79.4604}); // Caledonia-Fairbank
        FSA_CENTROIDS.put("M6G", new double[]{43.6698, -79.4194}); // Christie Pits
        FSA_CENTROIDS.put("M6H", new double[]{43.6598, -79.4416}); // Dufferin Grove
        FSA_CENTROIDS.put("M6J", new double[]{43.6494, -79.4220}); // Trinity-Bellwoods
        FSA_CENTROIDS.put("M6K", new double[]{43.6384, -79.4316}); // Parkdale
        FSA_CENTROIDS.put("M6L", new double[]{43.7130, -79.4876}); // North Park
        FSA_CENTROIDS.put("M6M", new double[]{43.6921, -79.4677}); // Del Ray
        FSA_CENTROIDS.put("M6N", new double[]{43.6701, -79.4903}); // Runnymede
        FSA_CENTROIDS.put("M6P", new double[]{43.6614, -79.4641}); // High Park North
        FSA_CENTROIDS.put("M6R", new double[]{43.6487, -79.4513}); // Roncesvalles
        FSA_CENTROIDS.put("M6S", new double[]{43.6479, -79.4789}); // Swansea
        FSA_CENTROIDS.put("M7A", new double[]{43.6623, -79.3893}); // Queen's Park
        // ── Etobicoke ─────────────────────────────────────────────────────
        FSA_CENTROIDS.put("M8V", new double[]{43.6053, -79.5015}); // New Toronto
        FSA_CENTROIDS.put("M8W", new double[]{43.6017, -79.5434}); // Alderwood
        FSA_CENTROIDS.put("M8X", new double[]{43.6524, -79.5098}); // The Kingsway
        FSA_CENTROIDS.put("M8Y", new double[]{43.6326, -79.4836}); // Mimico East
        FSA_CENTROIDS.put("M8Z", new double[]{43.6176, -79.5339}); // Mimico West
        FSA_CENTROIDS.put("M9A", new double[]{43.6666, -79.5148}); // Islington
        FSA_CENTROIDS.put("M9B", new double[]{43.6476, -79.5533}); // Cloverdale
        FSA_CENTROIDS.put("M9C", new double[]{43.6393, -79.5936}); // Eringate
        FSA_CENTROIDS.put("M9L", new double[]{43.7576, -79.5756}); // Humber Summit
        FSA_CENTROIDS.put("M9M", new double[]{43.7302, -79.5444}); // Humberlea
        FSA_CENTROIDS.put("M9N", new double[]{43.7062, -79.5165}); // Weston
        FSA_CENTROIDS.put("M9P", new double[]{43.6966, -79.5302}); // Westmount
        FSA_CENTROIDS.put("M9R", new double[]{43.6882, -79.5573}); // Kingsview Village
        FSA_CENTROIDS.put("M9V", new double[]{43.7453, -79.5883}); // Rexdale
        FSA_CENTROIDS.put("M9W", new double[]{43.7067, -79.5953}); // Humberwood
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
     * Accepts ROOFTOP, RANGE_INTERPOLATED, and GEOMETRIC_CENTER results.
     * GEOMETRIC_CENTER is the postal code centroid (~200 m accuracy), sufficient
     * for worker-matching distance pricing.  APPROXIMATE is still rejected.
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
                if (type == LocationType.ROOFTOP || type == LocationType.RANGE_INTERPOLATED
                        || type == LocationType.GEOMETRIC_CENTER) {
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
