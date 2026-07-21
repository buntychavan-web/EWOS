package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayComponentKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ArrearResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID payrollRunId,
        String reasonCode,
        String description,
        BigDecimal amount,
        PayComponentKind kind,
        LocalDate forPeriodStart,
        LocalDate forPeriodEnd,
        boolean applied,
        Instant appliedAt,
        long versionNo) {}
