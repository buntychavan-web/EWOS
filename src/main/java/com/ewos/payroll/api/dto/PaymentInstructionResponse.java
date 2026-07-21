package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PaymentInstructionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentInstructionResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID bankAdviceId,
        UUID payslipId,
        UUID employeeId,
        UUID employeeBankAccountId,
        String bankName,
        String accountHolder,
        String accountNumberMasked,
        String routingCode,
        String swiftBic,
        BigDecimal amount,
        String currency,
        PaymentInstructionStatus status,
        String settlementReference,
        Instant settledAt,
        String failureReason,
        long versionNo) {}
