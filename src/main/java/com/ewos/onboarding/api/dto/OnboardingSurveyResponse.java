package com.ewos.onboarding.api.dto;

import com.ewos.onboarding.domain.OnboardingSurveyType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OnboardingSurveyResponse(
        UUID id,
        UUID planId,
        OnboardingSurveyType surveyType,
        String responsesJson,
        BigDecimal overallRating,
        String comments,
        Instant submittedAt,
        long versionNo) {}
