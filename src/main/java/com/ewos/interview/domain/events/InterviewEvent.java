package com.ewos.interview.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event covering interview-lifecycle changes. Published on {@code AFTER_COMMIT} to the Kafka
 * topic {@code ewos.interview.event}.
 */
public record InterviewEvent(
        InterviewEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID applicationId,
        UUID roundId,
        UUID templateId,
        UUID candidateId,
        String detail,
        UUID actorId,
        Instant occurredAt) {}
