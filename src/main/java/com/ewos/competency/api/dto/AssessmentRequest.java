package com.ewos.competency.api.dto;

import com.ewos.competency.domain.AssessmentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AssessmentRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID employeeId,
        @NotNull UUID competencyId,
        @NotNull AssessmentType assessmentType,
        @Min(0) int assessedLevel,
        @Size(max = 256) String assessorName,
        @Size(max = 4000) String comments) {}
