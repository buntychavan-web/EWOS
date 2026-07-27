package com.ewos.identity.application;

import java.util.Set;
import java.util.UUID;

/**
 * Sprint 1.4 Role Usage Impact Analysis (Product Owner addition) — resolves which companies and
 * departments a set of users (typically: everyone holding a given role) actually work in, by
 * walking their linked {@code Employee} records. Mirrors {@link TenantClaimResolver}/{@link
 * EmployeeClaimResolver}'s dependency-inversion shape exactly: defined here so {@code
 * com.ewos.identity} stays free of a compile-time dependency on {@code com.ewos.employee}; {@code
 * com.ewos.employee} provides the one implementation.
 */
public interface RoleCompanyUsageResolver {

    /**
     * A user with no linked {@code Employee} (Sprint 1.3's {@code employees.user_id}) contributes
     * to no company or department entry — not an error, just nothing to report for that user.
     */
    RoleCompanyUsage resolveUsage(Set<UUID> userIds);
}
