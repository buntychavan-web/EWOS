package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateGratuityConfigurationRequest(
        @DecimalMin("0.01") BigDecimal statutoryCeiling,
        @Min(1) Integer rateNumerator,
        @Min(1) Integer rateDenominator,
        @DecimalMin("0.00") BigDecimal minYearsEligibility,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
