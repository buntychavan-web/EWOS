package com.ewos.payroll.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateProfessionalTaxSlabRequest(
        @Pattern(regexp = "^(MALE|FEMALE)$") String gender,
        @DecimalMin("0.00") BigDecimal minMonthlyIncome,
        BigDecimal maxMonthlyIncome,
        @DecimalMin("0.00") BigDecimal monthlyTaxAmount,
        BigDecimal annualCapAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Boolean active) {}
