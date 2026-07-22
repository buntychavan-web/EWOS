package com.ewos.competency.api.dto;

import com.ewos.competency.domain.AssessmentType;
import java.time.Instant;
import java.util.UUID;

public record AssessmentResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID competencyId,
        AssessmentType assessmentType,
        int assessedLevel,
        UUID assessedBy,
        String assessorName,
        String comments,
        Instant assessedAt) {}
