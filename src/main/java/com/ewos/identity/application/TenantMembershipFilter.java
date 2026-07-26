package com.ewos.identity.application;

import java.util.Set;
import java.util.UUID;

/**
 * Sprint 1.4 audit remediation, Finding 1 — narrows a set of users down to only those who belong to a
 * given tenant. Needed because a system role (e.g. {@code SYSTEM_ADMIN}) can legitimately be held by users
 * across every tenant on the platform, but reporting on it (Role Usage Impact Analysis) must never expose
 * another tenant's users, companies, or departments to the caller.
 *
 * <p>Mirrors {@link TenantClaimResolver}/{@link EmployeeClaimResolver}'s dependency-inversion shape: defined
 * here so {@code com.ewos.identity} stays free of a compile-time dependency on {@code com.ewos.tenancy};
 * {@code com.ewos.tenancy} provides the one implementation, since it alone owns {@code
 * user_tenant_memberships}.
 */
public interface TenantMembershipFilter {

    /** Users with no membership in {@code tenantId} (including users with no membership at all) are
     * dropped, not errored — this is a narrowing filter, not a validation. */
    Set<UUID> filterToTenant(Set<UUID> userIds, UUID tenantId);
}
