package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PayrollSimulationReportResponse(
        UUID tenantId,
        UUID companyId,
        UUID payrollPeriodId,
        Instant simulatedAt,
        int employeesSimulated,
        BigDecimal totalSimulatedGross,
        BigDecimal totalSimulatedDeductions,
        BigDecimal totalSimulatedNet,
        BigDecimal totalSimulatedTds,
        BigDecimal totalSimulatedEmployerContributions,
        int abnormalSalaryChangeCount,
        PayrollValidationReportResponse validation,
        List<PayrollSimulationLineResponse> lines) {}
