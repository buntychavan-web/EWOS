package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdatePayGroupRequest(
        @Size(max = 128) String name,
        @Size(max = 512) String description,
        PayrollFrequency frequency,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Min(1) @Max(31) Integer payDayOfMonth,
        Boolean active) {}
