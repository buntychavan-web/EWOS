package com.ewos.identity.api.dto;

import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/** Partial update — null fields are left unchanged. Rejected for system roles (see Sprint 1.4 SDD §6). */
public record UpdateRoleRequest(
        @Size(max = 100) String name, @Size(max = 500) String description, Set<UUID> permissionIds) {}
