package com.yosnowmow.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility methods for cryptographic hashing.
 *
 * Used by:
 *   - GeocodingService — SHA-256 cache keys for geocode results
 *   - AuditLogService  — SHA-256 hash chaining for the audit log (P1-20)
 */
public final class HashUtils {

    private HashUtils() {
        // Utility class — not instantiable
    }

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of the given string.
     *
     * @param input the string to hash (UTF-8 encoded)
     * @return 64-character lowercase hex string
     * @throws RuntimeException (wrapping {@link NoSuchAlgorithmException}) if the
     *         JVM does not support SHA-256 (this never happens in practice)
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the Java Security Standard Algorithm Names spec
            throw new RuntimeException("SHA-256 not available on this JVM", e);
        }
    }

    /**
     * Returns the lowercase hex-encoded SHA-256 digest of the concatenation of all
     * provided parts, separated by null bytes (to prevent length-extension attacks
     * from adjacent fields blending together).
     *
     * Used by AuditLogService to build the hash chain.
     *
     * @param parts values to concatenate and hash
     * @return 64-character lowercase hex string
     */
    public static String sha256Parts(String... parts) {
        return sha256(String.join("\0", parts));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
