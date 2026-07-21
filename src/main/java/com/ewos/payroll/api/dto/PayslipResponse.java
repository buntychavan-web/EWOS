package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayslipStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PayslipResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID payrollRunId,
        UUID payrollPeriodId,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate payDate,
        String currency,
        BigDecimal grossAmount,
        BigDecimal deductionsAmount,
        BigDecimal netAmount,
        BigDecimal lopDays,
        BigDecimal basicEffective,
        PayslipStatus status,
        Instant finalizedAt,
        List<PayslipLineResponse> lines,
        long versionNo) {}
