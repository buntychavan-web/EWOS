package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PfConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        BigDecimal wageCeiling,
        BigDecimal epsWageCeiling,
        BigDecimal employeeRatePct,
        BigDecimal employerPfRatePct,
        BigDecimal epsRatePct,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
