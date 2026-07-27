package com.ewos.employee.application;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Resolves the current request's authenticated employee, if any. The employee is derived from the
 * JWT's {@code employeeId} claim — set by {@code JwtAuthenticationFilter} as a request attribute at
 * authentication time — mirroring {@code com.ewos.tenancy.application.TenantContext#homeTenantId()}
 * exactly.
 *
 * <p>Unlike {@code TenantContext.homeTenantId()}, absence is not surfaced as an error here: a
 * caller with no linked employee (or an ambiguous multi-company link, left unresolved at login
 * time) is a normal, common state, not a broken session. Callers that require a linked employee
 * (e.g. {@code GET /employees/me}) decide how to respond to an empty result themselves.
 */
@Component
public class EmployeeContext {

    /** Must match {@code JwtAuthenticationFilter.EMPLOYEE_ID_REQUEST_ATTRIBUTE} exactly. */
    private static final String EMPLOYEE_ID_REQUEST_ATTRIBUTE =
            "com.ewos.employee.currentEmployeeId";

    public Optional<UUID> currentEmployeeId() {
        Object attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }
        Object value = servletAttributes.getRequest().getAttribute(EMPLOYEE_ID_REQUEST_ATTRIBUTE);
        return value instanceof UUID employeeId ? Optional.of(employeeId) : Optional.empty();
    }
}
