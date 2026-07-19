package com.ewos.identity.infrastructure.security.ratelimit;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for per-account lockout after repeated failed logins. Reasonable production
 * defaults; every knob is env-overridable.
 */
@ConfigurationProperties(prefix = "app.security.lockout")
public record AccountLockoutProperties(boolean enabled, int maxAttempts, Duration lockDuration) {

    public AccountLockoutProperties {
        if (maxAttempts < 1) {
            maxAttempts = 5;
        }
        if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
            lockDuration = Duration.ofMinutes(15);
        }
    }
}
