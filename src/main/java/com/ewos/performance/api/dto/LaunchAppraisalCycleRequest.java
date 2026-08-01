package com.ewos.performance.api.dto;

import com.ewos.employee.domain.EmployeeStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Sprint 24B bulk launch filter. {@code organizationUnitIds} may reference any node in the
 * company's org hierarchy regardless of its {@code unitType} — business unit, department, location,
 * and cost centre are all just {@link com.ewos.organization.domain.OrganizationUnit} rows in this
 * platform, so one id-list filter with {@code includeDescendants} covers every one of them
 * uniformly rather than needing a separate parameter per org-unit-type concept. Grade, designation,
 * and employee-category filters are not offered here because {@code Employee} has no such
 * attributes today — see the Sprint 24B completion report for the rationale.
 */
public record LaunchAppraisalCycleRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID templateId,
        List<UUID> organizationUnitIds,
        Boolean includeDescendants,
        UUID employmentTypeId,
        EmployeeStatus employeeStatus) {

    public boolean resolvedIncludeDescendants() {
        return includeDescendants == null || includeDescendants;
    }

    public EmployeeStatus resolvedEmployeeStatus() {
        return employeeStatus == null ? EmployeeStatus.ACTIVE : employeeStatus;
    }
}
