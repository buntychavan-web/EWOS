package com.ewos.payroll.api.dto.reports;

import java.math.BigDecimal;
import java.util.UUID;

/** Aggregate KPI panel for the payroll operator dashboard. */
public record PayrollDashboardResponse(
        UUID tenantId,
        UUID companyId,
        int payrollPeriodsOpen,
        int payrollPeriodsLocked,
        int payrollRunsInFlight,
        int payslipsFinalizedLast30d,
        int bankAdvicesPending,
        int statutoryChallansDraft,
        int finalSettlementsPending,
        BigDecimal totalPaidLast30d,
        BigDecimal totalStatutoryLast30d) {}
