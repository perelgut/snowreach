package com.yosnowmow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS configuration.
 *
 * Allowed origins are loaded from application.yml under yosnowmow.cors.allowed-origins
 * so they can be overridden per environment without code changes.
 *
 * Credentials (Authorization header) are allowed so that Firebase ID tokens
 * can be sent from the React frontend on every API request.
 */
@Configuration
public class CorsConfig {

    private final CorsProperties corsProperties;

    public CorsConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed origins loaded from config (see application.yml)
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());

        // All standard REST methods plus OPTIONS (required for preflight)
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Authorization carries the Firebase ID token; Content-Type and Accept
        // are standard REST headers
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // Required so the Authorization header is sent with cross-origin requests
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour to reduce OPTIONS requests
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }

    /**
     * Binds yosnowmow.cors.* from application.yml into a typed config object.
     */
    @Configuration
    @ConfigurationProperties(prefix = "yosnowmow.cors")
    public static class CorsProperties {

        private List<String> allowedOrigins = List.of("http://localhost:5173");

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
