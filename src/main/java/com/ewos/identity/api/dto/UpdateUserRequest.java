package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

@Schema(description = "Patch payload for user profile updates. All fields are optional; nulls are ignored.")
public record UpdateUserRequest(
        @Schema(description = "New email address.")
        @Email @Size(max = 255) String email,

        @Schema(description = "Full replacement of assigned roles. Null → keep current roles.")
        Set<UUID> roleIds
) {
}
