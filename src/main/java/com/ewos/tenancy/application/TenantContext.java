package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the current request's authenticated tenant. The tenant is derived from the JWT's
 * {@code tenantId} claim — set by {@code JwtAuthenticationFilter} as a request attribute at
 * authentication time — never from client-supplied input, closing the gap where {@code
 * X-Tenant-Id} was previously trusted with no server-side verification.
 *
 * <p>The request-attribute name below is intentionally a plain literal duplicated on both the
 * producer ({@code JwtAuthenticationFilter}, {@code com.ewos.identity}) and this consumer ({@code
 * com.ewos.tenancy}) rather than a shared constant class: this mirrors how the JWT's {@code
 * "authorities"} claim key is already independently duplicated between {@code
 * JwtAuthenticationFilter} and {@code AuthenticationService} today, and keeps {@code
 * com.ewos.tenancy} from taking a compile-time dependency on {@code com.ewos.identity} — a
 * direction that doesn't exist anywhere else in the codebase.
 */
@Component
public class TenantContext {

    /** Must match {@code JwtAuthenticationFilter.TENANT_ID_REQUEST_ATTRIBUTE} exactly. */
    private static final String TENANT_ID_REQUEST_ATTRIBUTE = "com.ewos.tenancy.currentTenantId";

    /**
     * The tenant the current caller belongs to, per their own {@code UserTenantMembership} —
     * resolved once at authentication time and carried on the JWT, not re-queried per call.
     *
     * @throws ApiException 403 if no tenant could be resolved for this request (an authenticated
     *     caller whose account has no membership yet — a real, admin-actionable state, not a bug).
     */
    public UUID homeTenantId() {
        ServletRequestAttributes attributes = currentRequestAttributes();
        Object value = attributes.getRequest().getAttribute(TENANT_ID_REQUEST_ATTRIBUTE);
        if (!(value instanceof UUID tenantId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "No tenant is resolved for the current session — contact an administrator to"
                            + " complete account setup");
        }
        return tenantId;
    }

    /** Same idiom used platform-wide to resolve the authenticated caller: the JWT filter puts the
     * user's UUID string into {@code Authentication#getName()}. */
    public Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private static ServletRequestAttributes currentRequestAttributes() {
        Object attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "No request context available to resolve tenant");
        }
        return servletAttributes;
    }
}
