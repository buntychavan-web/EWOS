package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.PayrollCollaborationScope;
import com.ewos.tenancy.domain.PayrollCollaborationStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PayrollCollaborationResponse(
        UUID id,
        UUID clientId,
        UUID providerId,
        PayrollCollaborationScope scope,
        PayrollCollaborationStatus status,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        Integer slaDays,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy,
        long versionNo) {}
