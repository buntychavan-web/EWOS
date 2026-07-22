package com.ewos.competency.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record RoleCompetencyResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID orgUnitId,
        String designation,
        UUID competencyId,
        int requiredLevel,
        BigDecimal weightage,
        String notes) {}
