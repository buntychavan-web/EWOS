package com.ewos.goals.api.dto;

import com.ewos.goals.domain.GoalPriority;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
import com.ewos.goals.domain.GoalType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record GoalResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID libraryGoalId,
        UUID parentGoalId,
        String code,
        String name,
        String description,
        GoalType goalType,
        GoalScope scope,
        UUID employeeId,
        UUID orgUnitId,
        UUID performanceCycleId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal weightage,
        String target,
        String unitOfMeasure,
        String currentValue,
        BigDecimal progressPercent,
        GoalStatus status,
        GoalPriority priority,
        BigDecimal reviewScore,
        String reviewNotes,
        Instant reviewedAt,
        UUID reviewedBy,
        Instant closedAt,
        UUID closedBy) {}
