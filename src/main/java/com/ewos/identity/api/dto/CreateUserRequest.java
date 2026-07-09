package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

@Schema(
        description =
                "Payload for creating a new user. Password is validated against the configured policy.")
public record CreateUserRequest(
        @Schema(example = "jane.doe", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(min = 3, max = 150)
                String username,
        @Schema(example = "jane.doe@ewos.local", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Email
                @Size(max = 255)
                String email,
        @Schema(example = "T3mp0rary!Pass", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String password,
        @Schema(description = "Role UUIDs to assign. Empty or null → no roles.") Set<UUID> roleIds,
        @Schema(description = "Optional enabled flag; defaults to true.", example = "true")
                Boolean enabled) {}
