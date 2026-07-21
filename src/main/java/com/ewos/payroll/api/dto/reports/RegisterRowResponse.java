package com.ewos.payroll.api.dto.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Row in a salary / payroll register: one line per payslip. */
public record RegisterRowResponse(
        UUID payslipId,
        UUID payrollRunId,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate payDate,
        String currency,
        BigDecimal basicEffective,
        BigDecimal lopDays,
        BigDecimal grossAmount,
        BigDecimal deductionsAmount,
        BigDecimal netAmount,
        String runType,
        String status) {}
