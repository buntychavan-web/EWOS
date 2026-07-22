package com.ewos.competency.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record RoleCompetencyRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        UUID orgUnitId,
        @Size(max = 256) String designation,
        @NotNull UUID competencyId,
        @Min(0) int requiredLevel,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal weightage,
        @Size(max = 2000) String notes) {}
