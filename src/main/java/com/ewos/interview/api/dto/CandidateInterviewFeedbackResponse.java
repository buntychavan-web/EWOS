package com.ewos.interview.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CandidateInterviewFeedbackResponse(
        UUID id,
        UUID roundId,
        UUID candidateId,
        BigDecimal ratingExperience,
        BigDecimal ratingProcess,
        Boolean wouldReapply,
        String comments,
        Instant submittedAt,
        long versionNo) {}
