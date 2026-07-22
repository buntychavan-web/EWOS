package com.ewos.goals.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering goal lifecycle changes. */
public record GoalEvent(
        GoalEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID goalId,
        UUID libraryItemId,
        UUID employeeId,
        UUID orgUnitId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
