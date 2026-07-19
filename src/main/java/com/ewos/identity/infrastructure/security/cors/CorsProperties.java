package com.ewos.identity.infrastructure.security.cors;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS configuration bound from {@code app.cors.*}. Per-profile YAML files override the defaults —
 * dev + test allow local Vite / CRA origins; prod ships with an <em>empty</em> allowed-origins list
 * so a deployment must explicitly opt origins in via {@code APP_CORS_ALLOWED_ORIGINS}.
 *
 * <p>The bean is validated at start-up by {@link CorsConfig}. Wildcards ({@code *}) are always
 * rejected in the {@code prod} profile — see ADR-0004.
 */
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

    /** Origins to allow. Exact matches. Never {@code *} in prod. */
    private List<String> allowedOrigins = List.of();

    /** Origin patterns (Spring notation). Empty by default. */
    private List<String> allowedOriginPatterns = List.of();

    /** HTTP methods to allow. */
    private List<String> allowedMethods =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD");

    /** Request headers the browser may send. */
    private List<String> allowedHeaders =
            List.of(
                    "Authorization",
                    "Content-Type",
                    "Accept",
                    "Origin",
                    "X-Request-ID",
                    "X-Requested-With");

    /** Response headers the browser is allowed to read. */
    private List<String> exposedHeaders = List.of("X-Request-ID", "Location");

    /**
     * Whether the browser may send cookies / Authorization on cross-origin requests. Required
     * whenever the frontend sends the JWT — leave {@code true} unless you know why not.
     */
    private boolean allowCredentials = true;

    /** How long the browser may cache the preflight response. */
    private Duration maxAge = Duration.ofHours(1);

    /**
     * Path patterns the CORS config applies to. Defaults to everything — the whole API is
     * cross-origin-consumable.
     */
    private List<String> pathPatterns = List.of("/**");

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns =
                allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods == null ? List.of() : List.copyOf(allowedMethods);
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders == null ? List.of() : List.copyOf(allowedHeaders);
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders == null ? List.of() : List.copyOf(exposedHeaders);
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge == null ? Duration.ofHours(1) : maxAge;
    }

    public List<String> getPathPatterns() {
        return pathPatterns;
    }

    public void setPathPatterns(List<String> pathPatterns) {
        this.pathPatterns =
                pathPatterns == null || pathPatterns.isEmpty()
                        ? List.of("/**")
                        : List.copyOf(pathPatterns);
    }
}
