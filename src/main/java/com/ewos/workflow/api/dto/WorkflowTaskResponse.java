package com.ewos.workflow.api.dto;

import com.ewos.workflow.domain.WorkflowActorType;
import com.ewos.workflow.domain.WorkflowTaskStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowTaskResponse(
        UUID id,
        UUID tenantId,
        UUID instanceId,
        UUID stateId,
        String stateCode,
        WorkflowActorType assigneeActorType,
        UUID assigneeActorId,
        String assigneeRoleCode,
        String actionCode,
        Instant dueAt,
        WorkflowTaskStatus status,
        Instant completedAt,
        UUID completedBy,
        String outcomeCode,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        long versionNo) {}
