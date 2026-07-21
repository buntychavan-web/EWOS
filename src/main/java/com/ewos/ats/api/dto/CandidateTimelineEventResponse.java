package com.ewos.ats.api.dto;

import com.ewos.ats.domain.TimelineEventType;
import java.time.Instant;
import java.util.UUID;

public record CandidateTimelineEventResponse(
        UUID id,
        UUID candidateId,
        UUID applicationId,
        TimelineEventType eventType,
        String eventSummary,
        String eventData,
        UUID actorId,
        Instant occurredAt) {}
