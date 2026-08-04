package com.ewos.payroll.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LtaBlockConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        int blockDurationYears,
        int anchorBlockStartYear,
        int maxExemptClaimsPerBlock,
        boolean carryForwardEnabled,
        int carryForwardMaxClaims,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        boolean active,
        String notes) {}
