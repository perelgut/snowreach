package com.yosnowmow.config;

import com.yosnowmow.security.FirebaseTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration.
 *
 * Authentication model:
 *   - Stateless — no HTTP session, no cookie
 *   - Every request must carry a Firebase ID token in the Authorization header
 *   - CSRF disabled (not applicable to Bearer token REST APIs)
 *
 * Public endpoints (no token required):
 *   /api/health         — simple health check
 *   /actuator/**        — Spring Boot Actuator (health probe for Cloud Run)
 *   /webhooks/**        — Stripe and other incoming webhooks (verified by signature)
 *
 * All other endpoints require a valid Firebase ID token.
 * Role enforcement is handled by RbacInterceptor + @RequiresRole, not here.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final FirebaseTokenFilter firebaseTokenFilter;

    public SecurityConfig(FirebaseTokenFilter firebaseTokenFilter) {
        this.firebaseTokenFilter = firebaseTokenFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless — Firebase ID token on every request, no session
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF not needed for Bearer token APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Public and protected path rules
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/health", "/actuator/**", "/webhooks/**").permitAll()
                    .anyRequest().authenticated())

            // Run Firebase token verification before Spring's default auth filter
            .addFilterBefore(firebaseTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
