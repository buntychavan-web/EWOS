package com.ewos.identity.application;

import java.util.UUID;

/**
 * Sprint 1.4 audit remediation, Finding 2 — ensures a user has at least one active tenant membership,
 * backfilling to the platform's bootstrap tenant if they have none. Exists specifically for {@link
 * IdentityBootstrap}: {@code V38__tenant_resolution.sql}'s backfill runs once, at migration time, over
 * whatever is already in {@code users} — on a genuinely fresh deployment that's nobody, since the bootstrap
 * admin is created afterward by {@link IdentityBootstrap}, an {@code ApplicationRunner} that runs after
 * Flyway migrations complete. Without this, the bootstrap admin could never resolve a tenant at all.
 *
 * <p>Mirrors {@link TenantClaimResolver}/{@link EmployeeClaimResolver}'s dependency-inversion shape: defined
 * here so {@code com.ewos.identity} stays free of a compile-time dependency on {@code com.ewos.tenancy};
 * {@code com.ewos.tenancy} provides the one implementation, since it alone owns {@code
 * user_tenant_memberships} and knows the bootstrap tenant's id.
 */
public interface DefaultTenantMembershipProvisioner {

    /** No-op if the user already has an active membership. */
    void ensureDefaultMembership(UUID userId);
}
