package com.ewos.competency.api.dto;

import com.ewos.competency.domain.DevelopmentPlanStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        String title,
        String description,
        LocalDate startsOn,
        LocalDate endsOn,
        DevelopmentPlanStatus status,
        Instant completedAt) {}
