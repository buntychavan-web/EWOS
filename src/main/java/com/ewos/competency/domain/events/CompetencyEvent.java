package com.ewos.competency.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering competency lifecycle changes. */
public record CompetencyEvent(
        CompetencyEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID competencyId,
        UUID employeeId,
        UUID planId,
        UUID assessmentId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
