package com.ewos.interview.api.dto;

import com.ewos.interview.domain.InterviewType;
import java.util.UUID;

public record InterviewTemplateResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        InterviewType interviewType,
        int defaultDurationMinutes,
        String scorecardSchema,
        boolean active,
        long versionNo) {}
