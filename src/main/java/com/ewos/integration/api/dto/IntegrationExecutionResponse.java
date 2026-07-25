package com.ewos.integration.api.dto;

import com.ewos.integration.domain.ErrorClassification;
import com.ewos.integration.domain.IntegrationAdapterType;
import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntegrationExecutionResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        UUID dataExchangeRecordId,
        UUID configurationId,
        IntegrationAdapterType adapterType,
        int attemptNumber,
        IntegrationExecutionOutcome outcome,
        ErrorClassification errorClassification,
        String errorMessage,
        Instant startedAt,
        Instant completedAt,
        Long durationMs,
        UUID actorId) {}
