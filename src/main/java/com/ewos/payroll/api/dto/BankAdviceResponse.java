package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.BankAdviceFormat;
import com.ewos.payroll.domain.BankAdviceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record BankAdviceResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID payrollRunId,
        String adviceNumber,
        LocalDate adviceDate,
        String currency,
        BankAdviceFormat fileFormat,
        int totalCount,
        BigDecimal totalAmount,
        BankAdviceStatus status,
        Instant generatedAt,
        UUID generatedBy,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        Instant settledAt,
        String notes,
        List<PaymentInstructionResponse> instructions,
        long versionNo) {}
