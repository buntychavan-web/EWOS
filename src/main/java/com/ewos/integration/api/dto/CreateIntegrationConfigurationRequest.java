package com.ewos.integration.api.dto;

import com.ewos.integration.domain.IntegrationAdapterType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateIntegrationConfigurationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 64) String exchangeType,
        @NotNull IntegrationAdapterType adapterType,
        @NotBlank @Size(max = 4000) String configJson) {}
