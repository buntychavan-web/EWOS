package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollApprovalDecisionType;
import java.time.Instant;
import java.util.UUID;

public record PayrollApprovalDecisionResponse(
        UUID id,
        int levelNumber,
        PayrollApprovalDecisionType decision,
        UUID decidedBy,
        Instant decidedAt,
        String comments) {}
