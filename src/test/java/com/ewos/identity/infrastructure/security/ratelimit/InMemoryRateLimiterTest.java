package com.ewos.identity.infrastructure.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    @Test
    void allowsUntilLimitReached() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.allow("k", 5, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.allow("k", 5, Duration.ofMinutes(1))).isFalse();
    }

    @Test
    void differentKeysAreIndependent() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        assertThat(limiter.allow("a", 1, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.allow("a", 1, Duration.ofMinutes(1))).isFalse();
        assertThat(limiter.allow("b", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void resetClearsCounter() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        limiter.allow("k", 1, Duration.ofMinutes(1));
        limiter.reset("k");
        assertThat(limiter.allow("k", 1, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void degenerateInputsShortCircuitToAllow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        assertThat(limiter.allow(null, 5, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.allow("", 5, Duration.ofMinutes(1))).isTrue();
        assertThat(limiter.allow("k", 0, Duration.ofMinutes(1))).isTrue();
    }
}
