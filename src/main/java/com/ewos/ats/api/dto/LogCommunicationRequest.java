package com.ewos.ats.api.dto;

import com.ewos.ats.domain.CommunicationChannel;
import com.ewos.ats.domain.CommunicationDirection;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record LogCommunicationRequest(
        @NotNull CommunicationChannel channel,
        @NotNull CommunicationDirection direction,
        UUID applicationId,
        @Size(max = 512) String subject,
        @Size(max = 4000) String bodySummary,
        @Size(max = 512) String externalRef,
        Instant occurredAt) {}
