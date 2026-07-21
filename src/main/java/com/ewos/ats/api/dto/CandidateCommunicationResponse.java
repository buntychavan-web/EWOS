package com.ewos.ats.api.dto;

import com.ewos.ats.domain.CommunicationChannel;
import com.ewos.ats.domain.CommunicationDirection;
import java.time.Instant;
import java.util.UUID;

public record CandidateCommunicationResponse(
        UUID id,
        UUID candidateId,
        UUID applicationId,
        CommunicationChannel channel,
        CommunicationDirection direction,
        String subject,
        String bodySummary,
        String externalRef,
        Instant occurredAt,
        UUID sentBy,
        long versionNo) {}
