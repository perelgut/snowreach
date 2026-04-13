package com.yosnowmow.util;

/**
 * Stateless geographic utility methods.
 *
 * Used by GeocodingService (address normalisation, FSA extraction) and
 * MatchingService (distance calculation for Worker search and tier pricing).
 */
public final class GeoUtils {

    /** Earth's mean radius in kilometres (WGS-84 approximation used by Google Maps). */
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtils() {
        // Utility class — not instantiable
    }

    // ── Distance ─────────────────────────────────────────────────────────────

    /**
     * Calculates the great-circle distance between two points on Earth using the
     * Haversine formula.
     *
     * Accurate to within ~0.5% for the distances involved in YoSnowMow
     * (typically 1–25 km); sufficient for Worker matching and tier pricing.
     *
     * @param lat1 latitude of point 1 in decimal degrees
     * @param lon1 longitude of point 1 in decimal degrees
     * @param lat2 latitude of point 2 in decimal degrees
     * @param lon2 longitude of point 2 in decimal degrees
     * @return distance in kilometres
     */
    public static double haversineDistanceKm(double lat1, double lon1,
                                              double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // ── Address normalisation ─────────────────────────────────────────────────

    /**
     * Normalises an address string for use as a cache key.
     *
     * Transforms to lowercase and collapses all runs of whitespace (including
     * tabs and newlines) to a single space, then trims leading/trailing space.
     *
     * Example: "  456 Oak Ave,  Etobicoke, ON  M8Y 2B3  " → "456 oak ave, etobicoke, on m8y 2b3"
     *
     * @param address raw address string
     * @return normalised address string, or empty string if input is null/blank
     */
    public static String normalizeAddress(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        return address.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    // ── Postal code helpers ───────────────────────────────────────────────────

    /**
     * Extracts the FSA (Forward Sortation Area) from a Canadian postal code.
     *
     * The FSA is the first three characters of the postal code (e.g. "M8Y" from
     * "M8Y 2B3").  Whitespace is stripped before extraction so both "M8Y2B3"
     * and "M8Y 2B3" work correctly.
     *
     * @param postalCode raw postal code string
     * @return 3-character FSA in uppercase, or {@code null} if the postal code
     *         is null, too short, or blank
     */
    public static String extractFSA(String postalCode) {
        if (postalCode == null || postalCode.isBlank()) {
            return null;
        }
        String stripped = postalCode.trim().replace(" ", "").toUpperCase();
        if (stripped.length() < 3) {
            return null;
        }
        return stripped.substring(0, 3);
    }

    /**
     * Scans an address string for a Canadian postal code pattern (A1A 1A1 or A1A1A1)
     * and returns the FSA if found.
     *
     * @param address full address string (e.g. "456 Oak Ave, Etobicoke, ON M8Y 2B3")
     * @return FSA (e.g. "M8Y"), or {@code null} if no postal code pattern is found
     */
    public static String extractFSAFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        // Canadian postal code: letter-digit-letter space? digit-letter-digit
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b([A-Za-z]\\d[A-Za-z])\\s?\\d[A-Za-z]\\d\\b")
                .matcher(address);
        if (m.find()) {
            return m.group(1).toUpperCase();
        }
        return null;
    }
}
