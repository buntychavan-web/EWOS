package com.ewos.identity.api.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RoleResponse(
        UUID id,
        UUID tenantId,
        boolean systemRole,
        String name,
        String description,
        Set<PermissionResponse> permissions,
        Instant createdAt,
        Instant updatedAt,
        UUID createdBy,
        UUID updatedBy) {}
