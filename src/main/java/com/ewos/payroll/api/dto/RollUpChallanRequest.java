package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record RollUpChallanRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Pattern(regexp = "^[A-Z0-9_-]+$") String jurisdiction,
        @NotBlank @Pattern(regexp = "^[A-Z0-9_-]+$") String code,
        @NotNull @Min(190001) @Max(300012) Integer periodMonth) {}
