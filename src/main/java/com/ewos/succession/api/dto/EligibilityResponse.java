package com.ewos.succession.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EligibilityResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID careerPathId,
        boolean eligible,
        Integer tenureMonths,
        BigDecimal lastRating,
        Integer competencyGap,
        String notes,
        Instant assessedAt,
        UUID assessedBy) {}
