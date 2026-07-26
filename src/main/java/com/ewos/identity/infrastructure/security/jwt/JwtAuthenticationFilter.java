package com.ewos.identity.infrastructure.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String TENANT_ID_CLAIM = "tenantId";
    private static final String EMPLOYEE_ID_CLAIM = "employeeId";

    /**
     * Request-attribute name the resolved tenant is published under, read by {@code
     * com.ewos.tenancy.application.TenantContext}. Kept as an independently-duplicated literal
     * rather than a shared constant — see that class's Javadoc for why.
     */
    private static final String TENANT_ID_REQUEST_ATTRIBUTE = "com.ewos.tenancy.currentTenantId";

    /**
     * Request-attribute name the resolved employee is published under, read by {@code
     * com.ewos.employee.application.EmployeeContext}. Same independently-duplicated-literal idiom as
     * {@link #TENANT_ID_REQUEST_ATTRIBUTE}.
     */
    private static final String EMPLOYEE_ID_REQUEST_ATTRIBUTE = "com.ewos.employee.currentEmployeeId";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header)
                && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                Jws<Claims> jws = jwtService.parse(token);
                Claims claims = jws.getPayload();

                Collection<GrantedAuthority> authorities = extractAuthorities(claims);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                claims.getSubject(), null, authorities);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                extractTenantId(claims)
                        .ifPresent(
                                tenantId ->
                                        request.setAttribute(TENANT_ID_REQUEST_ATTRIBUTE, tenantId));
                extractUuidClaim(claims, EMPLOYEE_ID_CLAIM)
                        .ifPresent(
                                employeeId ->
                                        request.setAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE, employeeId));
            } catch (JwtException ex) {
                log.debug("Rejected JWT: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private static Collection<GrantedAuthority> extractAuthorities(Claims claims) {
        Object raw = claims.get(AUTHORITIES_CLAIM);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(v -> v instanceof String)
                .map(String.class::cast)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    private static Optional<UUID> extractTenantId(Claims claims) {
        return extractUuidClaim(claims, TENANT_ID_CLAIM);
    }

    private static Optional<UUID> extractUuidClaim(Claims claims, String claimName) {
        Object raw = claims.get(claimName);
        if (!(raw instanceof String value) || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException ex) {
            log.debug("JWT {} claim is not a valid UUID: {}", claimName, value);
            return Optional.empty();
        }
    }
}
