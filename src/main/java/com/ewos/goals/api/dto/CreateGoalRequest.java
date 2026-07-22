package com.ewos.goals.api.dto;

import com.ewos.goals.domain.GoalPriority;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGoalRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        UUID libraryGoalId,
        UUID parentGoalId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @NotNull GoalType goalType,
        @NotNull GoalScope scope,
        UUID employeeId,
        UUID orgUnitId,
        UUID performanceCycleId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal weightage,
        @Size(max = 256) String target,
        @Size(max = 64) String unitOfMeasure,
        GoalPriority priority) {}
