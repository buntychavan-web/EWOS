package com.ewos.identity.infrastructure.security.ratelimit;

import com.ewos.shared.exception.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles the Employee/Manager Self-Service surface, per employee and per IP (PRD §11 Security
 * Architecture, Business Rule 12, audit finding 3.5).
 *
 * <p>Reuses {@link InMemoryRateLimiter} exactly as {@link AuthRateLimitFilter} does for login
 * throttling — same sliding-window primitive, two independent keys instead of one. Unlike {@code
 * AuthRateLimitFilter} (which must run <em>before</em> Spring Security's authentication filters,
 * since login itself has no identity yet), this filter needs the caller's employee id to already be
 * resolvable, so it is wired in {@code SecurityConfig} via {@code addFilterAfter(...,
 * TenantHeaderValidationFilter.class)} — after both JWT authentication and tenant-header validation
 * have run.
 *
 * <p>Reads the {@code employeeId} claim the same way {@code com.ewos.employee.application
 * .EmployeeContext} does — via the {@code com.ewos.employee.currentEmployeeId} request attribute
 * {@code JwtAuthenticationFilter} sets — rather than importing {@code EmployeeContext} itself.
 * {@code com.ewos.identity} deliberately has no compile-time dependency on {@code com.ewos
 * .employee} anywhere in this codebase (see {@code JwtAuthenticationFilter}'s own javadoc: it sets
 * that attribute under a private constant, not a shared import, for exactly this reason); this
 * filter preserves that boundary rather than introducing the first such dependency.
 *
 * <p>Matches any request whose path contains {@code /self-service} or {@code /manager-self-service}
 * — every existing and future ESS/MSS endpoint follows one of those two path conventions (see the
 * PRD's API tables), so this filter does not need a maintained list of every module's self-service
 * base path.
 */
@Component
@EnableConfigurationProperties(EssMssRateLimitProperties.class)
public class EssMssRateLimitFilter extends OncePerRequestFilter {

    /** Must match {@code EmployeeContext.EMPLOYEE_ID_REQUEST_ATTRIBUTE} exactly. */
    private static final String EMPLOYEE_ID_REQUEST_ATTRIBUTE =
            "com.ewos.employee.currentEmployeeId";

    private static final String ESS_MARKER = "/self-service";
    private static final String MSS_MARKER = "/manager-self-service";

    private final InMemoryRateLimiter rateLimiter;
    private final EssMssRateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public EssMssRateLimitFilter(
            InMemoryRateLimiter rateLimiter,
            EssMssRateLimitProperties properties,
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
        return !(path.contains(ESS_MARKER) || path.contains(MSS_MARKER));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        String ipKey = "ip|" + path + "|" + extractClientIp(request);
        if (!rateLimiter.allow(ipKey, properties.perIpMaxAttempts(), properties.window())) {
            reject(request, response, "IP");
            return;
        }

        UUID employeeId = currentEmployeeId(request);
        if (employeeId != null) {
            String employeeKey = "employee|" + path + "|" + employeeId;
            int limit = properties.effectiveLimitFor(path);
            if (!rateLimiter.allow(employeeKey, limit, properties.window())) {
                reject(request, response, "employee");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static UUID currentEmployeeId(HttpServletRequest request) {
        Object value = request.getAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE);
        return value instanceof UUID employeeId ? employeeId : null;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String scope)
            throws IOException {
        ApiError body =
                ApiError.of(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        "Too Many Requests",
                        "Rate limit exceeded on "
                                + request.getRequestURI()
                                + " ("
                                + scope
                                + ") — slow down and retry after "
                                + properties.window(),
                        request.getRequestURI());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", Long.toString(properties.window().toSeconds()));
        objectMapper.writeValue(response.getWriter(), body);
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
