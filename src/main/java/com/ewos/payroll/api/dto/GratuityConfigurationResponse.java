package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record GratuityConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        BigDecimal statutoryCeiling,
        int rateNumerator,
        int rateDenominator,
        BigDecimal minYearsEligibility,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        long versionNo) {}
