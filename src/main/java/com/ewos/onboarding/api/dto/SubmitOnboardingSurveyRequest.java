package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingSurveyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record SubmitOnboardingSurveyRequest(
        @NotNull OnboardingSurveyType surveyType,
        String responsesJson,
        @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal overallRating,
        @Size(max = 8000) String comments) {}
