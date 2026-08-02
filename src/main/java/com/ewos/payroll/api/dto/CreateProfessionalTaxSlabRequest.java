package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateProfessionalTaxSlabRequest(
        @NotNull UUID tenantId,
        @NotNull UUID jurisdictionId,
        @Pattern(regexp = "^(MALE|FEMALE)$") String gender,
        @NotNull @DecimalMin("0.00") BigDecimal minMonthlyIncome,
        BigDecimal maxMonthlyIncome,
        @NotNull @DecimalMin("0.00") BigDecimal monthlyTaxAmount,
        BigDecimal annualCapAmount,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
