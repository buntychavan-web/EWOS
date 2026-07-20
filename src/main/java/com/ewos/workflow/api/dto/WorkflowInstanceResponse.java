package com.ewos.workflow.api.dto;

import com.ewos.workflow.domain.WorkflowInstanceStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WorkflowInstanceResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID definitionId,
        String definitionCode,
        int definitionVersion,
        String subjectType,
        UUID subjectId,
        UUID currentStateId,
        String currentStateCode,
        WorkflowInstanceStatus status,
        Instant startedAt,
        Instant completedAt,
        String correlationKey,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
