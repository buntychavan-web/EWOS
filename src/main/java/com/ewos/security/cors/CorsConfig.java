package com.ewos.security.cors;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Registers the {@link CorsConfigurationSource} bean that Spring Security picks up when {@code
 * http.cors(Customizer.withDefaults())} is set.
 *
 * <p>Rules (see ADR-0004):
 *
 * <ul>
 *   <li>Origins are read from {@code app.cors.allowedOrigins} — per-profile YAML supplies
 *       env-appropriate defaults; production reads {@code APP_CORS_ALLOWED_ORIGINS} and ships empty
 *       by default (deny-by-default).
 *   <li>In the {@code prod} profile a wildcard ({@code *}) origin is rejected at start-up. Same for
 *       a wildcard combined with {@code allowCredentials=true} (browsers reject that anyway, so we
 *       fail fast instead of at request time).
 *   <li>Non-prod profiles log a warning when {@code *} is used, but do not fail — this lets a
 *       developer explore quickly while making the smell visible.
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public final class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);
    private static final String WILDCARD = "*";
    private static final String PROD_PROFILE = "prod";

    private final CorsProperties properties;
    private final Environment environment;

    public CorsConfig(CorsProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
        validate();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        if (!properties.getAllowedOrigins().isEmpty()) {
            cors.setAllowedOrigins(properties.getAllowedOrigins());
        }
        if (!properties.getAllowedOriginPatterns().isEmpty()) {
            cors.setAllowedOriginPatterns(properties.getAllowedOriginPatterns());
        }
        cors.setAllowedMethods(properties.getAllowedMethods());
        cors.setAllowedHeaders(properties.getAllowedHeaders());
        cors.setExposedHeaders(properties.getExposedHeaders());
        cors.setAllowCredentials(properties.isAllowCredentials());
        cors.setMaxAge(properties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        for (String pattern : properties.getPathPatterns()) {
            source.registerCorsConfiguration(pattern, cors);
        }
        return source;
    }

    private void validate() {
        boolean prod = isProdProfile();
        List<String> origins = properties.getAllowedOrigins();
        List<String> patterns = properties.getAllowedOriginPatterns();

        boolean wildcardOrigin = origins.contains(WILDCARD);
        boolean wildcardPattern = patterns.contains(WILDCARD);

        if (prod && (wildcardOrigin || wildcardPattern)) {
            throw new IllegalStateException(
                    "CORS: wildcard origin '*' is not permitted in the 'prod' profile. "
                            + "Configure APP_CORS_ALLOWED_ORIGINS with an explicit list.");
        }

        if (prod && origins.isEmpty() && patterns.isEmpty()) {
            log.warn(
                    "CORS: production profile is active but no allowed origins are configured — "
                            + "cross-origin requests will be rejected. Set APP_CORS_ALLOWED_ORIGINS "
                            + "to enable specific origins.");
        }

        if (properties.isAllowCredentials() && (wildcardOrigin || wildcardPattern)) {
            throw new IllegalStateException(
                    "CORS: wildcard origin '*' cannot be combined with allowCredentials=true. "
                            + "Either specify explicit origins or set app.cors.allowCredentials=false.");
        }

        if (!prod && (wildcardOrigin || wildcardPattern)) {
            log.warn(
                    "CORS: wildcard origin '*' is enabled in a non-prod profile. Do not deploy this"
                            + " configuration to production.");
        }
    }

    private boolean isProdProfile() {
        for (String p : environment.getActiveProfiles()) {
            if (PROD_PROFILE.equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }
}
