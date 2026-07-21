package com.ewos.workflow.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable domain event covering all workflow-engine happenings. Published on {@code AFTER_COMMIT}
 * to Kafka topic {@code ewos.workflow.event}.
 */
public record WorkflowEvent(
        WorkflowEventType eventType,
        UUID instanceId,
        UUID definitionId,
        UUID tenantId,
        UUID companyId,
        String subjectType,
        UUID subjectId,
        String fromStateCode,
        String toStateCode,
        String actionCode,
        UUID taskId,
        UUID actorId,
        Instant occurredAt) {}
