package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

@Schema(
        description =
                "Creates a tenant-scoped custom role. tenant_id is always taken from the caller's own"
                        + " session, never from this body. Only permissions the caller already holds may"
                        + " be assigned.")
public record CreateRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @Schema(description = "Permission UUIDs to assign. Empty or null -> no permissions.")
                Set<UUID> permissionIds) {}
