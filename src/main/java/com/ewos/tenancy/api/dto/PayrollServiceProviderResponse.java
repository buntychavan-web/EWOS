package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.ProviderStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PayrollServiceProviderResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        ProviderStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
