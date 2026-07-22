package com.ewos.succession.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering succession lifecycle changes. */
public record SuccessionEvent(
        SuccessionEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID careerPathId,
        UUID poolId,
        UUID planId,
        UUID assessmentId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
