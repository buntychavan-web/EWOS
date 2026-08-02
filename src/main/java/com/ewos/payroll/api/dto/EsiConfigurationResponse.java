package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EsiConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        BigDecimal wageThreshold,
        BigDecimal employeeRatePct,
        BigDecimal employerRatePct,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
