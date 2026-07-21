package com.ewos.payroll.api.dto.reports;

import java.math.BigDecimal;
import java.util.UUID;

/** Executive-view roll-up: high-level payroll totals for the last N pay periods. */
public record ExecutiveDashboardResponse(
        UUID tenantId,
        UUID companyId,
        int coveredPeriods,
        BigDecimal totalGross,
        BigDecimal totalDeductions,
        BigDecimal totalNet,
        BigDecimal averageNetPerEmployee,
        int totalEmployees,
        int totalRunsFinalized) {}
