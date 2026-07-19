package com.ewos.identity.infrastructure.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthRateLimitFilterTest {

    private final ObjectMapper om = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void allowsAuthPathsUnderTheLimit() throws Exception {
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new RateLimitProperties(true, 5, Duration.ofMinutes(1), 5),
                        om);

        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(loginRequest(), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void blocksAuthPathsOverTheLimitWithRetryAfterHeader() throws Exception {
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new RateLimitProperties(true, 2, Duration.ofMinutes(1), 2),
                        om);
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

        // 2 pass, third is blocked
        for (int i = 0; i < 2; i++) {
            filter.doFilter(loginRequest(), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(loginRequest(), blocked, chain);

        assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blocked.getContentAsString()).contains("Rate limit");
    }

    @Test
    void skipsNonAuthPaths() throws Exception {
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new RateLimitProperties(true, 1, Duration.ofMinutes(1), 1),
                        om);
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/users");
        req.setRemoteAddr("10.0.0.1");
        // Even 100 hits shouldn't be throttled because this path isn't in the throttled list.
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    @Test
    void disabledConfigShortCircuits() throws Exception {
        AuthRateLimitFilter filter =
                new AuthRateLimitFilter(
                        new InMemoryRateLimiter(),
                        new RateLimitProperties(false, 1, Duration.ofMinutes(1), 1),
                        om);
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(200);

        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(loginRequest(), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
    }

    private static MockHttpServletRequest loginRequest() {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        r.setRemoteAddr("10.0.0.1");
        return r;
    }
}
