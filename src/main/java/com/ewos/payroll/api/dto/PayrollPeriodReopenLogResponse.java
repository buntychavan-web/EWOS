package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollPeriodStatus;
import java.time.Instant;
import java.util.UUID;

public record PayrollPeriodReopenLogResponse(
        UUID id,
        UUID payrollPeriodId,
        String reason,
        UUID reopenedBy,
        Instant reopenedAt,
        PayrollPeriodStatus previousStatus,
        PayrollPeriodStatus newStatus) {}
