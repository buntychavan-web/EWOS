package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PayrollPeriodResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        PayrollFrequency frequency,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate payDate,
        PayrollPeriodStatus status,
        Instant lockedAt,
        UUID lockedBy,
        Instant closedAt,
        UUID closedBy,
        long versionNo) {}
