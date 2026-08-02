package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import java.math.BigDecimal;
import java.util.UUID;

public record IncomeTaxSlabResponse(
        UUID id,
        UUID tenantId,
        TaxRegime regime,
        String fiscalYear,
        BigDecimal minIncome,
        BigDecimal maxIncome,
        BigDecimal ratePct,
        boolean active,
        long versionNo) {}
