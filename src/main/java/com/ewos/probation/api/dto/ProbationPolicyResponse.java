package com.ewos.probation.api.dto;

import java.util.UUID;

public record ProbationPolicyResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        int defaultPeriodDays,
        int maxExtensionDays,
        boolean allowEarlyConfirm,
        boolean active) {}
