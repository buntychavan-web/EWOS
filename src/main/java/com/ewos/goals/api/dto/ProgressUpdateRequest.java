package com.ewos.goals.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProgressUpdateRequest(
        @Size(max = 256) String currentValue,
        @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal progressPercent,
        @Size(max = 4000) String notes) {}
