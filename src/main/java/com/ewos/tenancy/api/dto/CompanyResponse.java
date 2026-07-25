package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.CompanyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompanyResponse(
        UUID id,
        UUID tenantId,
        UUID clientId,
        String code,
        String name,
        String countryCode,
        CompanyStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
