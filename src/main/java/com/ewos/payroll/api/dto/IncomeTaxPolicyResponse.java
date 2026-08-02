package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.TaxRegime;
import java.math.BigDecimal;
import java.util.UUID;

public record IncomeTaxPolicyResponse(
        UUID id,
        UUID tenantId,
        TaxRegime regime,
        String fiscalYear,
        BigDecimal rebateIncomeThreshold,
        BigDecimal rebateMaxAmount,
        BigDecimal cessRatePct,
        BigDecimal standardDeduction,
        BigDecimal chapterViaMaxDeduction,
        BigDecimal housePropertyLossCap,
        boolean active,
        long versionNo) {}
