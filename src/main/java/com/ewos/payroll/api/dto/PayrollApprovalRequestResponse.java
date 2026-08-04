package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollApprovalRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record PayrollApprovalRequestResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID payrollRunId,
        UUID preparerId,
        int totalLevels,
        int currentLevel,
        PayrollApprovalRequestStatus status,
        Instant submittedAt,
        Instant decidedAt) {}
