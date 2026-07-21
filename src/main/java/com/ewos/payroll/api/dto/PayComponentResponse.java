package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PayComponentResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        PayComponentKind kind,
        PayComponentCalculationType calculationType,
        BigDecimal defaultAmount,
        BigDecimal defaultPercentage,
        boolean taxable,
        boolean active,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt,
        long versionNo) {}
