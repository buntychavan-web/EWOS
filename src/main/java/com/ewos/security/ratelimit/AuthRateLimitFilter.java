package com.ewos.security.ratelimit;

import com.ewos.common.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the auth endpoints per client IP. Runs before {@code JwtAuthenticationFilter} so the
 * throttle applies even if the caller can't authenticate. Failures return {@code 429 Too Many
 * Requests} with a standard {@link ApiError} envelope.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@EnableConfigurationProperties(RateLimitProperties.class)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    /** Paths this filter throttles. Everything else falls through untouched. */
    private static final List<String> THROTTLED_PATHS =
            List.of("/api/v1/auth/login", "/api/v1/auth/refresh");

    private final InMemoryRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(
            InMemoryRateLimiter rateLimiter,
            RateLimitProperties properties,
            ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        for (String throttled : THROTTLED_PATHS) {
            if (throttled.equals(path)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String ip = extractClientIp(request);
        String key = request.getRequestURI() + "|" + ip;

        if (!rateLimiter.allow(key, properties.maxAttempts(), properties.window())) {
            ApiError body =
                    ApiError.of(
                            HttpStatus.TOO_MANY_REQUESTS.value(),
                            "Too Many Requests",
                            "Rate limit exceeded on "
                                    + request.getRequestURI()
                                    + " — slow down and retry after "
                                    + properties.window(),
                            request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", Long.toString(properties.window().toSeconds()));
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }
        chain.doFilter(request, response);
    }

    private static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
