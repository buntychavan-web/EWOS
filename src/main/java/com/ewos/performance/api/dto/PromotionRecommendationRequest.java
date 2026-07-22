package com.ewos.performance.api.dto;

import com.ewos.performance.domain.PromotionRecommendation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PromotionRecommendationRequest(
        @NotNull PromotionRecommendation recommendation, @Size(max = 2000) String notes) {}
