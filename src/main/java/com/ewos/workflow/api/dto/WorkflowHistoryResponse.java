package com.ewos.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowHistoryResponse(
        UUID id,
        UUID instanceId,
        UUID fromStateId,
        String fromStateCode,
        UUID toStateId,
        String toStateCode,
        String actionCode,
        UUID actorId,
        UUID taskId,
        String notes,
        Instant occurredAt) {}
