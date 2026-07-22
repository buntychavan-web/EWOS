package com.ewos.performance.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering performance lifecycle changes. */
public record PerformanceEvent(
        PerformanceEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID cycleId,
        UUID templateId,
        UUID appraisalId,
        UUID employeeId,
        UUID sessionId,
        UUID workflowInstanceId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
