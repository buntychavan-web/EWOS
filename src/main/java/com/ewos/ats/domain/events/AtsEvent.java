package com.ewos.ats.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event covering ATS-lifecycle changes. Published on {@code AFTER_COMMIT} to the Kafka topic
 * {@code ewos.ats.event}. All identifiers are optional so a single record can describe
 * candidate-level, application-level, and attachment-level events.
 */
public record AtsEvent(
        AtsEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID candidateId,
        UUID applicationId,
        String applicationNumber,
        UUID jobRequisitionId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
