package com.ewos.identity.infrastructure.security.ratelimit;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for {@link EssMssRateLimitFilter} — throttling on the Employee/Manager Self-Service
 * surface (PRD §11 Security Architecture, Business Rule 12, audit finding 3.5). Reuses {@link
 * InMemoryRateLimiter}, the same in-memory sliding-window implementation {@link
 * AuthRateLimitFilter} already uses for login throttling — its own javadoc already documents the
 * Redis-backed upgrade path for horizontal scaling, so no new limiter implementation is introduced
 * here.
 *
 * <p>{@code pathOverrides} lets a specific path prefix (e.g. a future document-download or
 * dashboard-widget endpoint) be throttled more tightly than the platform default, per PRD §11's
 * "tighter limits on dashboard-widget and document-download endpoints" — matched against the
 * request URI by {@link #effectiveLimitFor}, longest-prefix-wins. Empty by default; Sprint 27A
 * ships no endpoints that need an override yet, but the mechanism is real, not a stub.
 */
@ConfigurationProperties(prefix = "app.security.ess-mss-rate-limit")
public record EssMssRateLimitProperties(
        boolean enabled,
        int perEmployeeMaxAttempts,
        int perIpMaxAttempts,
        Duration window,
        Map<String, Integer> pathOverrides) {

    public EssMssRateLimitProperties {
        if (perEmployeeMaxAttempts < 1) {
            perEmployeeMaxAttempts = 100;
        }
        if (perIpMaxAttempts < 1) {
            perIpMaxAttempts = 300;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(1);
        }
        if (pathOverrides == null) {
            pathOverrides = Map.of();
        }
    }

    /**
     * The effective per-employee limit for {@code path}: the longest matching key in {@link
     * #pathOverrides}, or {@link #perEmployeeMaxAttempts} if none matches.
     */
    int effectiveLimitFor(String path) {
        String bestPrefix = null;
        for (String prefix : pathOverrides.keySet()) {
            if (path.startsWith(prefix)
                    && (bestPrefix == null || prefix.length() > bestPrefix.length())) {
                bestPrefix = prefix;
            }
        }
        return bestPrefix == null ? perEmployeeMaxAttempts : pathOverrides.get(bestPrefix);
    }
}
