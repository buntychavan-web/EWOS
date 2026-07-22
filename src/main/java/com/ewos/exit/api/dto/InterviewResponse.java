package com.ewos.exit.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InterviewResponse(
        UUID id,
        UUID tenantId,
        UUID resignationId,
        Instant conductedAt,
        UUID conductedBy,
        String interviewerName,
        BigDecimal rating,
        Boolean wouldRecommend,
        String responsesJson,
        String comments) {}
