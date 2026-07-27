package com.ewos.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDelegationResponse(
        UUID id,
        UUID tenantId,
        UUID delegatorActorId,
        UUID delegateActorId,
        String roleCode,
        Instant startsAt,
        Instant endsAt,
        boolean active,
        String notes,
        Instant createdAt,
        Instant updatedAt) {}
