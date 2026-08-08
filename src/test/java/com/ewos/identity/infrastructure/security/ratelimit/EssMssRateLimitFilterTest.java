package com.ewos.identity.infrastructure.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class EssMssRateLimitFilterTest {

    private static final String EMPLOYEE_ID_REQUEST_ATTRIBUTE =
            "com.ewos.employee.currentEmployeeId";

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());
    private final FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

    @Test
    void allowsSelfServicePathsUnderTheLimit() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(true, 5, 300, Duration.ofMinutes(1), null),
                        om);

        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(selfServiceRequest(UUID.randomUUID()), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void blocksAnEmployeeOverThePerEmployeeLimitWithRetryAfterHeader() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(true, 2, 300, Duration.ofMinutes(1), null),
                        om);
        UUID employeeId = UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            filter.doFilter(selfServiceRequest(employeeId), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(selfServiceRequest(employeeId), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("Rate limit");
    }

    @Test
    void differentEmployeesOnTheSamePathHaveIndependentLimits() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(true, 1, 300, Duration.ofMinutes(1), null),
                        om);
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();

        MockHttpServletResponse resA = new MockHttpServletResponse();
        filter.doFilter(selfServiceRequest(employeeA), resA, chain);
        MockHttpServletResponse resB = new MockHttpServletResponse();
        filter.doFilter(selfServiceRequest(employeeB), resB, chain);

        assertThat(resA.getStatus()).isEqualTo(200);
        assertThat(resB.getStatus()).isEqualTo(200);
    }

    @Test
    void blocksByIpEvenWithNoEmployeeIdResolved() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(true, 300, 1, Duration.ofMinutes(1), null),
                        om);
        MockHttpServletRequest req =
                new MockHttpServletRequest("GET", "/api/v1/leave/self-service/balances");
        req.setRemoteAddr("10.0.0.9");
        // No employeeId attribute set — anonymous/unauthenticated request shape.

        filter.doFilter(req, new MockHttpServletResponse(), chain);
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req, blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void skipsPathsThatAreNeitherSelfServiceNorManagerSelfService() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(true, 1, 1, Duration.ofMinutes(1), null),
                        om);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/employees");
        req.setRemoteAddr("10.0.0.1");
        req.setAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE, UUID.randomUUID());

        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void matchesManagerSelfServicePathsToo() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(true, 1, 300, Duration.ofMinutes(1), null),
                        om);
        UUID employeeId = UUID.randomUUID();
        MockHttpServletRequest req =
                new MockHttpServletRequest("GET", "/api/v1/manager-self-service/team");
        req.setRemoteAddr("10.0.0.1");
        req.setAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE, employeeId);

        filter.doFilter(req, new MockHttpServletResponse(), chain);
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req, blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    void disabledConfigShortCircuits() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(false, 1, 1, Duration.ofMinutes(1), null),
                        om);

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(selfServiceRequest(UUID.randomUUID()), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void pathOverrideAppliesATighterLimitThanTheDefault() throws Exception {
        EssMssRateLimitFilter filter =
                new EssMssRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new EssMssRateLimitProperties(
                                true,
                                100,
                                300,
                                Duration.ofMinutes(1),
                                Map.of("/api/v1/self-service/widgets", 1)),
                        om);
        UUID employeeId = UUID.randomUUID();
        MockHttpServletRequest req =
                new MockHttpServletRequest(
                        "GET", "/api/v1/self-service/widgets/notifications-self-service");
        req.setRemoteAddr("10.0.0.1");
        req.setAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE, employeeId);

        filter.doFilter(req, new MockHttpServletResponse(), chain);
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(req, blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    private static MockHttpServletRequest selfServiceRequest(UUID employeeId) {
        MockHttpServletRequest r =
                new MockHttpServletRequest("GET", "/api/v1/leave/self-service/balances");
        r.setRemoteAddr("10.0.0.1");
        r.setAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE, employeeId);
        return r;
    }
}
