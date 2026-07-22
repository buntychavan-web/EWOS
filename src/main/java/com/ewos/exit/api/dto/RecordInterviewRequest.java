package com.ewos.exit.api.dto;

import java.math.BigDecimal;

public record RecordInterviewRequest(
        String interviewerName,
        BigDecimal rating,
        Boolean wouldRecommend,
        String responsesJson,
        String comments) {}
