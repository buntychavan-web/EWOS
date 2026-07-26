package com.ewos.tenancy.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantAccessGrantResponse(
        UUID id,
        UUID userId,
        UUID tenantId,
        UUID grantedBy,
        String reason,
        Instant expiresAt,
        Instant revokedAt,
        UUID revokedBy,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        long versionNo) {}
