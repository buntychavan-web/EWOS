package com.ewos.identity.application;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the tenant to embed in a newly-issued JWT for a user. {@code com.ewos.identity} has no
 * compile-time dependency on {@code com.ewos.tenancy} anywhere else in the codebase; this port
 * preserves that — the tenancy module provides the one implementation, identity only depends on
 * this interface.
 */
public interface TenantClaimResolver {

    /** Empty when the user has no {@code UserTenantMembership} yet — a real, admin-actionable
     * state (e.g. a brand-new account mid-provisioning), not an error. */
    Optional<UUID> resolveTenantId(UUID userId);
}
