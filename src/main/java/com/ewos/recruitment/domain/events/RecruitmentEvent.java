package com.ewos.recruitment.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event covering recruitment lifecycle changes. Published on {@code AFTER_COMMIT} to the
 * Kafka topic {@code ewos.recruitment.event}. All identifiers are optional so a single record can
 * describe both position-level and requisition-level events.
 */
public record RecruitmentEvent(
        RecruitmentEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID jobPositionId,
        UUID jobRequisitionId,
        String requisitionNumber,
        UUID workflowInstanceId,
        UUID actorId,
        Instant occurredAt) {}
