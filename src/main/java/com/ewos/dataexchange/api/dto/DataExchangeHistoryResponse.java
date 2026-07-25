package com.ewos.dataexchange.api.dto;

import com.ewos.dataexchange.domain.DataExchangeStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DataExchangeHistoryResponse(
        UUID id,
        DataExchangeStatus fromStatus,
        DataExchangeStatus toStatus,
        UUID actorId,
        String notes,
        Instant occurredAt) {}
