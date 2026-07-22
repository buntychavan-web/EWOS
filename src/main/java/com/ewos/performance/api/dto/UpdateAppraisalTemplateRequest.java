package com.ewos.performance.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAppraisalTemplateRequest(
        @NotBlank @Size(max = 256) String name,
        @Size(max = 2000) String description,
        @Min(0) int ratingScaleMin,
        @Min(1) int ratingScaleMax,
        boolean active) {}
