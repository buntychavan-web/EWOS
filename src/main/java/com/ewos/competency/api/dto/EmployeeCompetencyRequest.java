package com.ewos.competency.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record EmployeeCompetencyRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull UUID competencyId,
        @Min(0) int currentLevel,
        Integer targetLevel,
        @Size(max = 2000) String notes) {}
