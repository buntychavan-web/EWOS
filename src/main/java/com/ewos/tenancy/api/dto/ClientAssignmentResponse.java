package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.ClientAssignmentScopeRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ClientAssignmentResponse(
        UUID id,
        UUID providerId,
        UUID userId,
        UUID clientId,
        UUID serviceId,
        ClientAssignmentScopeRole scopeRole,
        boolean active,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
