package com.ewos.competency.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCompetencyRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 4000) String description,
        @Size(max = 64) String category,
        @Min(0) int scaleMin,
        @Min(1) int scaleMax) {}
