package com.ewos.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-definitely-long-enough-for-hs256-signing";

    private JwtService service(String issuer) {
        return new JwtService(new JwtProperties(SECRET, issuer, Duration.ofMinutes(15), Duration.ofDays(7)));
    }

    @Test
    void generatesAndParsesToken() {
        JwtService service = service("ewos");

        String token = service.generateAccessToken("user-42", Map.of(
                "authorities", List.of("ROLE_SYSTEM_ADMIN", "USER_READ")
        ));

        Jws<Claims> parsed = service.parse(token);
        Claims claims = parsed.getPayload();

        assertThat(claims.getSubject()).isEqualTo("user-42");
        assertThat(claims.getIssuer()).isEqualTo("ewos");
        @SuppressWarnings("unchecked")
        List<String> authorities = (List<String>) claims.get("authorities");
        assertThat(authorities).containsExactly("ROLE_SYSTEM_ADMIN", "USER_READ");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService(new JwtProperties(SECRET, "ewos", Duration.ofMinutes(15), Duration.ofDays(7)));
        JwtService verifier = new JwtService(new JwtProperties(
                "another-secret-key-that-is-also-long-enough-for-hs256-signing-abc",
                "ewos", Duration.ofMinutes(15), Duration.ofDays(7)));

        String token = issuer.generateAccessToken("user-42", Map.of());

        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(SignatureException.class);
    }
}
