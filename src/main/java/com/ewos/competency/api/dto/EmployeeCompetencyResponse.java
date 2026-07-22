package com.ewos.competency.api.dto;

import java.time.Instant;
import java.util.UUID;

public record EmployeeCompetencyResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID competencyId,
        int currentLevel,
        Integer targetLevel,
        Instant lastAssessedAt,
        String notes) {}
