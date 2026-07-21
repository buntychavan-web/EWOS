package com.ewos.interview.api.dto;

import java.math.BigDecimal;
import java.util.List;

/** Aggregated view over a round's scorecards. */
public record RoundScorecardSummaryResponse(
        int submittedCount,
        BigDecimal averageRating,
        BigDecimal weightedRecommendationScore,
        boolean leansHire,
        List<InterviewScorecardResponse> scorecards) {}
