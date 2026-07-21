package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.FinalSettlementStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinalSettlementResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        LocalDate terminationDate,
        LocalDate lastWorkingDate,
        BigDecimal unusedLeaveDays,
        BigDecimal encashmentAmount,
        BigDecimal gratuityAmount,
        BigDecimal noticePayRecovery,
        BigDecimal noticePayReceivable,
        BigDecimal otherEarnings,
        BigDecimal otherDeductions,
        String currency,
        FinalSettlementStatus status,
        Instant approvedAt,
        UUID approvedBy,
        Instant settledAt,
        UUID settledBy,
        UUID settlementRunId,
        String notes,
        long versionNo) {}
