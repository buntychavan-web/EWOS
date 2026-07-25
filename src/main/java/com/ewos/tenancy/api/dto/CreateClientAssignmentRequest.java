package com.ewos.tenancy.api.dto;

import com.ewos.tenancy.domain.ClientAssignmentScopeRole;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreateClientAssignmentRequest(
        @NotNull UUID providerId,
        @NotNull UUID userId,
        @NotNull UUID clientId,
        UUID serviceId,
        @NotNull ClientAssignmentScopeRole scopeRole,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
