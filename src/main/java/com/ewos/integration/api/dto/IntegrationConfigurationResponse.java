package com.ewos.integration.api.dto;

import com.ewos.integration.domain.IntegrationAdapterType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntegrationConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String exchangeType,
        IntegrationAdapterType adapterType,
        String configJson,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
