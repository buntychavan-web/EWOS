package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PayrollRunComparisonLineResponse(
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        String status,
        BigDecimal baseGross,
        BigDecimal compareGross,
        BigDecimal grossChangeAmount,
        BigDecimal grossChangePercent,
        BigDecimal baseNet,
        BigDecimal compareNet) {}
