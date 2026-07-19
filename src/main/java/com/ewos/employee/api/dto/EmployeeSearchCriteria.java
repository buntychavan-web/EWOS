package com.ewos.employee.api.dto;

import com.ewos.employee.domain.EmployeeStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record EmployeeSearchCriteria(
        @NotNull UUID tenantId,
        UUID companyId,
        UUID primaryOrgUnitId,
        UUID managerEmployeeId,
        UUID employmentTypeId,
        EmployeeStatus status,
        String employeeNumber,
        String namePart,
        String workEmail) {}
