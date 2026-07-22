package com.ewos.succession.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record EligibilityRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        UUID careerPathId,
        boolean eligible,
        Integer tenureMonths,
        BigDecimal lastRating,
        Integer competencyGap,
        @Size(max = 4000) String notes) {}
