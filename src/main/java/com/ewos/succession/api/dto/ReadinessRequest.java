package com.ewos.succession.api.dto;

import com.ewos.succession.domain.ReadinessLevel;
import com.ewos.succession.domain.TalentTier;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record ReadinessRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        BigDecimal performanceScore,
        BigDecimal potentialScore,
        TalentTier tier,
        ReadinessLevel readiness,
        @Size(max = 4000) String notes) {}
