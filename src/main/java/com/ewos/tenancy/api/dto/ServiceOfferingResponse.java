package com.ewos.tenancy.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ServiceOfferingResponse(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        String category,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
