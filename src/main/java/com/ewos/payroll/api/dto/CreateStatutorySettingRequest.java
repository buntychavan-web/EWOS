package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateStatutorySettingRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 16) @Pattern(regexp = "^[A-Z0-9_-]+$") String jurisdiction,
        @NotBlank @Size(max = 128) String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 1024) String description,
        BigDecimal valueNumeric,
        @Size(max = 1024) String valueString,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
