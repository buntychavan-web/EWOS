package com.ewos.workflow.api.dto;

import com.ewos.workflow.domain.WorkflowActorType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record AssignTaskRequest(
        @NotNull WorkflowActorType assigneeActorType,
        UUID assigneeActorId,
        @Size(max = 64) String assigneeRoleCode,
        @Size(max = 64) String actionCode,
        Instant dueAt,
        @Size(max = 2048) String notes) {}
