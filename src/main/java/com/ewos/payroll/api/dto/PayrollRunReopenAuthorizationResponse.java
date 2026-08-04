package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollRunReopenAuthorizationStatus;
import java.time.Instant;
import java.util.UUID;

public record PayrollRunReopenAuthorizationResponse(
        UUID id,
        UUID payrollRunId,
        String reason,
        UUID authorizedBy,
        Instant authorizedAt,
        PayrollRunReopenAuthorizationStatus status,
        UUID consumedByRunId,
        Instant consumedAt,
        UUID revokedBy,
        Instant revokedAt) {}
