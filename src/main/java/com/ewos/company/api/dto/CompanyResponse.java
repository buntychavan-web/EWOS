package com.ewos.company.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Master + current (or as-of) effective version merged for reads. */
public record CompanyResponse(
        UUID id,
        UUID tenantId,
        String code,
        boolean active,
        CompanyVersionResponse currentVersion,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long version) {}
