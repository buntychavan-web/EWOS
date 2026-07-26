package com.ewos.identity.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the current request's tenant from the JWT-derived request attribute {@code
 * JwtAuthenticationFilter} already publishes for {@code com.ewos.tenancy.application.TenantContext} to
 * consume. Reading it a second time from within {@code com.ewos.identity} itself lets {@link RoleService}
 * and {@code UserService} tenant-scope role visibility/assignment without {@code com.ewos.identity} taking
 * a compile-time dependency on {@code com.ewos.tenancy} — the same "producer and consumer share only a
 * literal" idiom {@code JwtAuthenticationFilter}'s own Javadoc documents, applied in the other direction.
 */
@Component
public class RequestTenantContext {

    /** Must match {@code JwtAuthenticationFilter.TENANT_ID_REQUEST_ATTRIBUTE} exactly. */
    private static final String TENANT_ID_REQUEST_ATTRIBUTE = "com.ewos.tenancy.currentTenantId";

    public Optional<UUID> currentTenantId() {
        Object attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        Object value = servletAttributes.getRequest().getAttribute(TENANT_ID_REQUEST_ATTRIBUTE);
        return value instanceof UUID tenantId ? Optional.of(tenantId) : Optional.empty();
    }
}
