package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.LoanInstallmentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LoanInstallmentResponse(
        UUID id,
        int installmentNumber,
        BigDecimal emiAmount,
        BigDecimal principalComponent,
        BigDecimal interestComponent,
        LoanInstallmentStatus status,
        Instant recoveredAt) {}
