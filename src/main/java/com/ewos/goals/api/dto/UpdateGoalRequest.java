package com.ewos.goals.api.dto;

import com.ewos.goals.domain.GoalPriority;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateGoalRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal weightage,
        @Size(max = 256) String target,
        @Size(max = 64) String unitOfMeasure,
        GoalPriority priority) {}
