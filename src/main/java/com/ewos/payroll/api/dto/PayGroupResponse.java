package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayrollFrequency;
import java.util.UUID;

public record PayGroupResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        PayrollFrequency frequency,
        String currency,
        Integer payDayOfMonth,
        boolean active,
        long versionNo) {}
