package com.ewos.goals.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateGoalLibraryItemRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @Size(max = 64) String category,
        @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal defaultWeightage,
        @Size(max = 256) String defaultTarget,
        @Size(max = 64) String unitOfMeasure,
        boolean active) {}
