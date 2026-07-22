package com.ewos.performance.api.dto;

import com.ewos.performance.domain.CalibrationSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record CalibrationSessionResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID cycleId,
        String name,
        Instant scheduledAt,
        CalibrationSessionStatus status,
        UUID facilitatorId,
        String notes,
        Instant completedAt) {}
