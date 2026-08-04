package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateLwfConfigurationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID jurisdictionId,
        @NotNull @DecimalMin("0.00") BigDecimal employeeContribution,
        @NotNull @DecimalMin("0.00") BigDecimal employerContribution,
        @Size(max = 32) @Pattern(regexp = "^(\\d{1,2}(,\\d{1,2})*)?$") String remittanceMonths,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
