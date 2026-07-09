package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Schema(description = "User representation returned by the user-management API.")
public record UserResponse(
        UUID id,
        String username,
        String email,
        boolean enabled,
        boolean accountNonLocked,
        Set<RoleSummary> roles,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy
) {
}
