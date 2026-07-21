package com.ewos.interview.api.dto;

import com.ewos.interview.domain.ScorecardRecommendation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InterviewScorecardResponse(
        UUID id,
        UUID roundId,
        UUID interviewerId,
        BigDecimal overallRating,
        ScorecardRecommendation recommendation,
        String strengths,
        String weaknesses,
        String comments,
        String criteriaJson,
        Instant submittedAt,
        long versionNo) {}
