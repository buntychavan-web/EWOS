package com.ewos.interview.api.dto;

import com.ewos.interview.domain.ScorecardRecommendation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record SubmitScorecardRequest(
        @NotNull UUID interviewerId,
        @DecimalMin("0.00") @DecimalMax("10.00") BigDecimal overallRating,
        @NotNull ScorecardRecommendation recommendation,
        @Size(max = 4000) String strengths,
        @Size(max = 4000) String weaknesses,
        @Size(max = 8000) String comments,
        String criteriaJson) {}
