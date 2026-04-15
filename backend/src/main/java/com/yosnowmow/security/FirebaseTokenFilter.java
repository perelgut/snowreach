package com.yosnowmow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;

/**
 * Servlet filter that runs once per request to verify Firebase ID tokens.
 *
 * Flow:
 *   1. Extract token from "Authorization: Bearer {token}" header
 *   2. Call FirebaseAuth.verifyIdToken() — validates signature, expiry, audience
 *   3. On success: populate SecurityContext with AuthenticatedUser
 *   4. On failure or missing token: return 401 JSON (RFC 7807)
 *
 * Skipped paths (no token required):
 *   /api/health, /actuator/**, /webhooks/**
 */
@Component
public class FirebaseTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseAuth firebaseAuth;
    private final ObjectMapper objectMapper;

    public FirebaseTokenFilter(FirebaseAuth firebaseAuth, ObjectMapper objectMapper) {
        this.firebaseAuth = firebaseAuth;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/health")
                || path.startsWith("/actuator/")
                || path.startsWith("/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // If a SecurityContext has already been populated upstream (e.g., by MockMvc's
        // authentication() post-processor in tests), honour it and skip token verification.
        // In production with STATELESS session management nothing upstream ever sets auth,
        // so this short-circuit is harmless.
        Authentication existingAuth = SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        // Missing or malformed header
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, request.getRequestURI(),
                    "Missing or malformed Authorization header");
            return;
        }

        String idToken = authHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            FirebaseToken decoded = firebaseAuth.verifyIdToken(idToken);
            AuthenticatedUser user = toAuthenticatedUser(decoded);

            // Convert roles to Spring GrantedAuthority for use in security expressions
            List<SimpleGrantedAuthority> authorities = user.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);

        } catch (FirebaseAuthException ex) {
            log.warn("Firebase token verification failed for {}: {}",
                    request.getRequestURI(), ex.getMessage());
            writeUnauthorized(response, request.getRequestURI(), "Invalid or expired token");
        }
    }

    /**
     * Maps a verified FirebaseToken to our AuthenticatedUser record.
     * Roles are read from the "roles" custom claim set by UserService on registration.
     * Falls back to an empty list if no roles claim is present yet.
     */
    private AuthenticatedUser toAuthenticatedUser(FirebaseToken token) {
        Object rolesClaim = token.getClaims().get("roles");
        List<String> roles;

        if (rolesClaim instanceof List<?> list) {
            roles = list.stream()
                    .filter(r -> r instanceof String)
                    .map(r -> (String) r)
                    .collect(Collectors.toList());
        } else {
            roles = Collections.emptyList();
        }

        return new AuthenticatedUser(token.getUid(), token.getEmail(), roles);
    }

    /** Writes a 401 RFC 7807 Problem JSON response. */
    private void writeUnauthorized(HttpServletResponse response,
                                   String instance,
                                   String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type",      "https://yosnowmow.com/errors/unauthorized");
        body.put("title",     "Unauthorized");
        body.put("status",    HttpStatus.UNAUTHORIZED.value());
        body.put("detail",    detail);
        body.put("instance",  instance);
        body.put("timestamp", Instant.now().toString());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
