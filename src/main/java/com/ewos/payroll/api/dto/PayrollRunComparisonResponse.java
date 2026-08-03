package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PayrollRunComparisonResponse(
        UUID baseRunId,
        UUID compareRunId,
        BigDecimal baseTotalGross,
        BigDecimal compareTotalGross,
        BigDecimal totalGrossChangeAmount,
        BigDecimal totalGrossChangePercent,
        int newJoiners,
        int leavers,
        int changed,
        int unchanged,
        List<PayrollRunComparisonLineResponse> lines) {}
