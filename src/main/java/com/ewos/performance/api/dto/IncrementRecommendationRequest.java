package com.ewos.performance.api.dto;

import com.ewos.performance.domain.IncrementRecommendation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record IncrementRecommendationRequest(
        @NotNull IncrementRecommendation recommendation,
        BigDecimal percent,
        @Size(max = 2000) String notes) {}
