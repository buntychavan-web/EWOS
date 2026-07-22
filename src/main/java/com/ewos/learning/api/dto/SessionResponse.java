package com.ewos.learning.api.dto;

import com.ewos.learning.domain.TrainingSessionStatus;
import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID courseId,
        String name,
        Instant startsAt,
        Instant endsAt,
        String venue,
        String trainerName,
        UUID trainerEmployeeId,
        Integer capacity,
        TrainingSessionStatus status,
        String notes) {}
