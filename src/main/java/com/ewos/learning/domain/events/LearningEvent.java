package com.ewos.learning.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering learning lifecycle changes. */
public record LearningEvent(
        LearningEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID courseId,
        UUID pathId,
        UUID sessionId,
        UUID enrollmentId,
        UUID certificationId,
        UUID employeeId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
