package com.ewos.identity.infrastructure.security.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for per-IP throttling on the auth endpoints. In-memory sliding-window
 * implementation is bundled with this WP; a Redis-backed replacement is straightforward when
 * horizontal scaling is turned on.
 */
@ConfigurationProperties(prefix = "app.security.rate-limit")
public record RateLimitProperties(
        boolean enabled, int maxAttempts, Duration window, int burstCapacity) {

    public RateLimitProperties {
        if (maxAttempts < 1) {
            maxAttempts = 60;
        }
        if (window == null || window.isNegative() || window.isZero()) {
            window = Duration.ofMinutes(1);
        }
        if (burstCapacity < 1) {
            burstCapacity = maxAttempts;
        }
    }
}
