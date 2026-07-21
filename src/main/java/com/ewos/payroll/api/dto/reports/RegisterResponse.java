package com.ewos.payroll.api.dto.reports;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Envelope for register-style report responses: rows plus aggregate totals. */
public record RegisterResponse(
        String reportCode,
        UUID tenantId,
        UUID companyId,
        int rowCount,
        BigDecimal totalGross,
        BigDecimal totalDeductions,
        BigDecimal totalNet,
        List<RegisterRowResponse> rows) {}
