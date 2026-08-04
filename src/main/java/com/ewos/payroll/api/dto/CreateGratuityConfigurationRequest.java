package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateGratuityConfigurationRequest(
        @NotNull UUID tenantId,
        UUID companyId,
        @NotNull @DecimalMin("0.01") BigDecimal statutoryCeiling,
        @Min(1) Integer rateNumerator,
        @Min(1) Integer rateDenominator,
        @DecimalMin("0.00") BigDecimal minYearsEligibility,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
