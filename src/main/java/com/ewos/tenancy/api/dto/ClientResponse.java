package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.ClientStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientResponse(
        UUID id,
        UUID tenantId,
        String code,
        String legalName,
        ClientStatus status,
        LocalDate onboardedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
