package com.ewos.tenancy.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateTenantAccessGrantRequest(
        @NotNull UUID userId,
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Future Instant expiresAt) {}
