package com.ewos.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.security.jwt.JwtProperties;
import com.ewos.security.jwt.JwtService;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Directional benchmarks for hot paths on the authentication flow. Excluded from the normal test
 * run — invoke explicitly with {@code mvn test -Dgroups=benchmark -Dtest=HotPathBenchmarks}.
 *
 * <p>These are NOT gates. They exist to catch order-of-magnitude regressions locally and to give
 * reviewers a stable number to point at when discussing perf tradeoffs.
 */
@Tag("benchmark")
class HotPathBenchmarks {

    @Test
    void jwtIssuanceBenchmark() {
        JwtProperties props =
                new JwtProperties(
                        "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
                        "ewos",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7));
        JwtService jwt = new JwtService(props);
        String subject = UUID.randomUUID().toString();
        Map<String, Object> claims = Map.of("scope", "USER_READ");

        BenchmarkSupport.BenchmarkResult result =
                BenchmarkSupport.run(
                        "jwt.issue.access",
                        200,
                        20,
                        200,
                        () -> jwt.generateAccessToken(subject, claims));

        // Loose sanity: a single HS256 sign should be sub-millisecond on any reasonable CI box.
        assertThat(result.mean().toNanos()).isLessThan(5_000_000L);
    }

    @Test
    void bcryptEncodeBenchmark() {
        // Strength 10 is what SecurityConfig uses; keep this in sync if that changes.
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String password = "Correct-Horse-Battery-Staple-1!";

        BenchmarkSupport.BenchmarkResult result =
                BenchmarkSupport.run(
                        "bcrypt.encode.strength10", 5, 10, 3, () -> encoder.encode(password));

        // BCrypt at strength 10 is intentionally slow. Just assert the number is plausible.
        assertThat(result.mean().toMillis()).isBetween(10L, 5_000L);
    }
}
