package com.ewos.performance.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CalibrationRequest(
        @NotNull BigDecimal calibratedRating,
        @Size(max = 32) String finalBand,
        @Size(max = 4000) String notes) {}
