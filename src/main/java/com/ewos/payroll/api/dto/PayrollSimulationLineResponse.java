package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One employee's simulated result for a dry-run payroll, alongside their previous finalized payslip
 * (if any) for comparison. Nothing behind this response is persisted — it is computed fresh from
 * the same calculation services a real run uses, then discarded.
 */
public record PayrollSimulationLineResponse(
        UUID employeeId,
        String employeeNumber,
        String employeeName,
        BigDecimal previousGross,
        BigDecimal previousNet,
        BigDecimal previousTds,
        BigDecimal simulatedGross,
        BigDecimal simulatedDeductions,
        BigDecimal simulatedNet,
        BigDecimal simulatedTds,
        BigDecimal simulatedEmployerPfContribution,
        BigDecimal simulatedEmployerEsiContribution,
        BigDecimal grossChangeAmount,
        BigDecimal grossChangePercent,
        boolean abnormalSalaryChange,
        List<String> notes) {}
