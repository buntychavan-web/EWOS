package com.ewos.workflow.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowDefinitionResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        String subjectType,
        int definitionVersion,
        boolean active,
        List<StateResponse> states,
        List<TransitionResponse> transitions,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
