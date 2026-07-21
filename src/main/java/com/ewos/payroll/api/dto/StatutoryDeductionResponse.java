package com.ewos.payroll.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StatutoryDeductionResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID payrollRunId,
        UUID payslipId,
        UUID employeeId,
        String jurisdiction,
        String code,
        int periodMonth,
        BigDecimal taxableBase,
        BigDecimal employeeContribution,
        BigDecimal employerContribution,
        BigDecimal totalAmount,
        String currency,
        UUID statutoryChallanId,
        long versionNo) {}
