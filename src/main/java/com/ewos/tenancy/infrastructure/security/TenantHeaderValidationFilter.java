package com.ewos.tenancy.infrastructure.security;

import com.ewos.shared.exception.ApiError;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantAccessGrantService;
import com.ewos.tenancy.application.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the {@code X-Tenant-Id} header — previously trusted as bare client-supplied input
 * across ~88 controllers with zero server-side check — against the caller's own JWT-derived tenant
 * ({@link TenantContext#homeTenantId()}), or an active {@link
 * com.ewos.tenancy.domain.TenantAccessGrant} covering a different tenant.
 *
 * <p>Deliberately does not remove or replace the header: every existing controller still reads
 * {@code X-Tenant-Id} exactly as before and uses that value in its service calls. This filter only
 * decides whether the caller is allowed to assert that value at all, closing the gap without
 * touching any of the ~88 existing controller signatures — see the Sprint 1 SDD's transition
 * strategy for why.
 */
@Component
public class TenantHeaderValidationFilter extends OncePerRequestFilter {

    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final TenantContext tenantContext;
    private final TenantAccessGrantService grantService;
    private final ObjectMapper objectMapper;

    public TenantHeaderValidationFilter(
            TenantContext tenantContext,
            TenantAccessGrantService grantService,
            ObjectMapper objectMapper) {
        this.tenantContext = tenantContext;
        this.grantService = grantService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String header = request.getHeader(TENANT_HEADER);
        return header == null
                || header.isBlank()
                || SecurityContextHolder.getContext().getAuthentication() == null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        UUID headerTenantId;
        try {
            headerTenantId = UUID.fromString(request.getHeader(TENANT_HEADER));
        } catch (IllegalArgumentException ex) {
            writeError(
                    response,
                    request,
                    HttpStatus.BAD_REQUEST,
                    "X-Tenant-Id header is not a valid UUID");
            return;
        }

        UUID homeTenantId;
        try {
            homeTenantId = tenantContext.homeTenantId();
        } catch (ApiException ex) {
            writeError(response, request, ex.getStatus(), ex.getMessage());
            return;
        }

        if (!headerTenantId.equals(homeTenantId)) {
            UUID userId = tenantContext.currentUserId().orElse(null);
            boolean granted = userId != null && grantService.hasActiveGrant(userId, headerTenantId);
            if (!granted) {
                writeError(
                        response,
                        request,
                        HttpStatus.FORBIDDEN,
                        "You do not have access to the requested tenant");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void writeError(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String message)
            throws IOException {
        ApiError body =
                ApiError.of(
                        status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
