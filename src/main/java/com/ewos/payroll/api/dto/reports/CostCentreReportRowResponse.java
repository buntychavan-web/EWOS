package com.ewos.payroll.api.dto.reports;

import java.math.BigDecimal;

public record CostCentreReportRowResponse(
        String costCentreCode,
        String glAccountCode,
        BigDecimal debitTotal,
        BigDecimal creditTotal) {}
