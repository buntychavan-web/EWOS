package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.TenantStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantResponse(
        UUID id,
        String code,
        String name,
        TenantStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
