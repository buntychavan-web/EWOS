package com.ewos.probation.api.dto;

import com.ewos.probation.domain.HrRecommendation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RecordHrRecommendationRequest(
        @NotNull HrRecommendation recommendation, @Size(max = 4000) String notes) {}
