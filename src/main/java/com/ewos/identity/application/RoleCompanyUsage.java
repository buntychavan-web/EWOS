package com.ewos.identity.application;

import java.util.List;
import java.util.UUID;

/**
 * Result of {@link RoleCompanyUsageResolver#resolveUsage(java.util.Set)} — owned by {@code
 * com.ewos.identity} (not {@code com.ewos.employee}) so the port's consumer never needs to depend on the
 * producer's types, mirroring how {@link TenantClaimResolver}/{@link EmployeeClaimResolver} return
 * identity-/JDK-owned types rather than the implementing module's own domain classes.
 */
public record RoleCompanyUsage(List<CompanyUsage> companies, List<DepartmentUsage> departments) {

    public record CompanyUsage(UUID companyId, long userCount) {}

    /** {@code orgUnitCode} may be null — not every linked employee has a primary org unit. */
    public record DepartmentUsage(UUID orgUnitId, String orgUnitCode, long userCount) {}
}
