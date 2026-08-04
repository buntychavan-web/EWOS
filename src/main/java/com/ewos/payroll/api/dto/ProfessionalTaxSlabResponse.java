package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ProfessionalTaxSlabResponse(
        UUID id,
        UUID tenantId,
        UUID jurisdictionId,
        String gender,
        BigDecimal minMonthlyIncome,
        BigDecimal maxMonthlyIncome,
        BigDecimal monthlyTaxAmount,
        BigDecimal annualCapAmount,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
