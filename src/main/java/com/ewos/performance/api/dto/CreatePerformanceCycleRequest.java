package com.ewos.performance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePerformanceCycleRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        LocalDate selfAssessmentDue,
        LocalDate managerAssessmentDue,
        LocalDate reviewerAssessmentDue,
        LocalDate calibrationDue,
        boolean bellCurveEnabled,
        @Size(max = 4000) String bellCurveConfigJson) {}
