package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import java.math.BigDecimal;
import java.util.UUID;

public record IncomeTaxSurchargeSlabResponse(
        UUID id,
        UUID tenantId,
        TaxRegime regime,
        String fiscalYear,
        BigDecimal minIncome,
        BigDecimal maxIncome,
        BigDecimal surchargeRatePct,
        boolean active,
        long versionNo) {}
