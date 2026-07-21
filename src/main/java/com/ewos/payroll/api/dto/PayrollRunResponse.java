package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollRunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayrollRunResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID payrollPeriodId,
        PayrollRunStatus status,
        Instant startedAt,
        UUID startedBy,
        Instant completedAt,
        Instant finalizedAt,
        UUID finalizedBy,
        Instant failedAt,
        String failureReason,
        int employeesProcessed,
        BigDecimal totalGross,
        BigDecimal totalDeductions,
        BigDecimal totalNet,
        long versionNo) {}
