package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreatePayGroupRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$") String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @NotNull PayrollFrequency frequency,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Min(1) @Max(31) Integer payDayOfMonth,
        Boolean active) {}
