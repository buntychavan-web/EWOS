package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollExceptionResponse(
        UUID payslipId,
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        String exceptionCode,
        String message,
        BigDecimal grossAmount,
        BigDecimal deductionsAmount,
        BigDecimal netAmount) {}
