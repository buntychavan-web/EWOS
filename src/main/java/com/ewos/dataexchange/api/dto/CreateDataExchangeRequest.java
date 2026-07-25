package com.ewos.dataexchange.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDataExchangeRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) String exchangeType,
        @Size(max = 128) String sourceEventType,
        @NotBlank @Size(max = 256) String correlationId,
        @Size(max = 4000) String payloadJson) {}
