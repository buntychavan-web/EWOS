package com.ewos.dataexchange.api.dto;

import com.ewos.dataexchange.domain.DataExchangeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataExchangeResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String exchangeType,
        String sourceEventType,
        String correlationId,
        String payloadJson,
        DataExchangeStatus status,
        int retryCount,
        Instant nextRetryAt,
        Instant acknowledgedAt,
        UUID acknowledgedBy,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
