package com.ewos.identity.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the employee to embed in a newly-issued JWT for a user, scoped to the tenant already
 * resolved for this login. {@code com.ewos.identity} has no compile-time dependency on {@code
 * com.ewos.employee} anywhere else in the codebase; this port preserves that — mirrors {@link
 * TenantClaimResolver} exactly. The employee module provides the one implementation.
 */
public interface EmployeeClaimResolver {

    /**
     * Empty when the user has no linked {@code Employee} in this tenant, or when they have more than one
     * (the multi-company edge case — same tenant, different companies) and the ambiguity can't be
     * resolved at login time. Both are normal, non-error states.
     */
    Optional<UUID> resolveEmployeeId(UUID userId, UUID tenantId);
}
