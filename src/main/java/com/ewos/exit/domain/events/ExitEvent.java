package com.ewos.exit.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering exit lifecycle changes. */
public record ExitEvent(
        ExitEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID resignationId,
        UUID employeeId,
        UUID clearanceId,
        UUID interviewId,
        UUID documentId,
        UUID alumniId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
