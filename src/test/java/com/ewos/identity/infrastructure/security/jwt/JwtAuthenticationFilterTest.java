package com.ewos.identity.infrastructure.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private static final String TENANT_ID_REQUEST_ATTRIBUTE = "com.ewos.tenancy.currentTenantId";

    private final JwtProperties properties =
            new JwtProperties(
                    "unit-test-secret-key-that-is-definitely-long-enough-for-hs256-signing",
                    "ewos-test",
                    Duration.ofMinutes(15),
                    Duration.ofDays(7));
    private final JwtService jwtService = new JwtService(properties);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService);
    private final FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesTenantIdClaimAsRequestAttributeWhenPresent() throws Exception {
        String subject = UUID.randomUUID().toString();
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", java.util.List.of("USER_READ"));
        claims.put("tenantId", tenantId.toString());
        String token = jwtService.generateAccessToken(subject, claims);

        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(TENANT_ID_REQUEST_ATTRIBUTE)).isEqualTo(tenantId);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo(subject);
    }

    @Test
    void omitsRequestAttributeWhenClaimAbsent() throws Exception {
        String subject = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", java.util.List.of("USER_READ"));
        String token = jwtService.generateAccessToken(subject, claims);

        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(TENANT_ID_REQUEST_ATTRIBUTE)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void omitsRequestAttributeWhenClaimIsNotAValidUuid() throws Exception {
        String subject = UUID.randomUUID().toString();
        Map<String, Object> claims = new HashMap<>();
        claims.put("authorities", java.util.List.of("USER_READ"));
        claims.put("tenantId", "not-a-uuid");
        String token = jwtService.generateAccessToken(subject, claims);

        MockHttpServletRequest request = bearerRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(request.getAttribute(TENANT_ID_REQUEST_ATTRIBUTE)).isNull();
    }

    @Test
    void skipsAuthenticationWhenNoBearerHeaderPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static MockHttpServletRequest bearerRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
