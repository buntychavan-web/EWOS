package com.ewos.succession.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateCareerPathRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 256) String fromDesignation,
        @NotBlank @Size(max = 256) String toDesignation,
        @Min(0) Integer minTenureMonths) {}
