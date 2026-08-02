package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LwfConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID jurisdictionId,
        BigDecimal employeeContribution,
        BigDecimal employerContribution,
        String remittanceMonths,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
