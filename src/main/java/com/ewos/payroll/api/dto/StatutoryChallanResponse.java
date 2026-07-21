package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.StatutoryChallanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StatutoryChallanResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String jurisdiction,
        String code,
        int periodMonth,
        int totalEmployees,
        BigDecimal totalTaxableBase,
        BigDecimal totalEmployeeContribution,
        BigDecimal totalEmployerContribution,
        BigDecimal totalAmount,
        String currency,
        StatutoryChallanStatus status,
        Instant filedAt,
        UUID filedBy,
        String filingReference,
        Instant paidAt,
        UUID paidBy,
        String paymentReference,
        String notes,
        long versionNo) {}
