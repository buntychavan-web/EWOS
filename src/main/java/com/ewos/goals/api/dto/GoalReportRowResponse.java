package com.ewos.goals.api.dto;

import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
import com.ewos.goals.domain.GoalType;
import java.math.BigDecimal;
import java.util.UUID;

public record GoalReportRowResponse(
        UUID goalId,
        String code,
        String name,
        GoalType goalType,
        GoalScope scope,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        GoalStatus status,
        BigDecimal weightage,
        BigDecimal progressPercent,
        BigDecimal reviewScore) {}
