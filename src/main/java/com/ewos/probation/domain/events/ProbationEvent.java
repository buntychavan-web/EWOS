package com.ewos.probation.domain.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event covering probation lifecycle changes. */
public record ProbationEvent(
        ProbationEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID recordId,
        UUID employeeId,
        UUID policyId,
        UUID workflowInstanceId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
