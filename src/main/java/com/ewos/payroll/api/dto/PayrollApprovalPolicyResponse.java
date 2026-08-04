package com.ewos.payroll.api.dto;

import java.util.List;
import java.util.UUID;

public record PayrollApprovalPolicyResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        boolean active,
        List<PayrollApprovalLevelResponse> levels) {}
