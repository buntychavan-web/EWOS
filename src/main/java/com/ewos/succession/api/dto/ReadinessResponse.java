package com.ewos.succession.api.dto;

import com.ewos.succession.domain.ReadinessLevel;
import com.ewos.succession.domain.TalentTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReadinessResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        BigDecimal performanceScore,
        BigDecimal potentialScore,
        TalentTier tier,
        ReadinessLevel readiness,
        String notes,
        Instant assessedAt,
        UUID assessedBy) {}
