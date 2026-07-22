package com.ewos.performance.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePerformanceCycleRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        LocalDate selfAssessmentDue,
        LocalDate managerAssessmentDue,
        LocalDate reviewerAssessmentDue,
        LocalDate calibrationDue,
        boolean bellCurveEnabled,
        @Size(max = 4000) String bellCurveConfigJson) {}
