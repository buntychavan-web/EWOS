package com.ewos.learning.api.dto;

import com.ewos.learning.domain.DeliveryMode;
import java.math.BigDecimal;
import java.util.UUID;

public record CourseResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String code,
        String name,
        String description,
        DeliveryMode deliveryMode,
        String provider,
        BigDecimal durationHours,
        BigDecimal cost,
        String currency,
        boolean certificationOffered,
        Integer certificationValidDays,
        boolean active) {}
