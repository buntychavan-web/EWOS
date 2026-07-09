package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Self-service password change. Requires the current password.")
public record ChangePasswordRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String currentPassword,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String newPassword
) {
}
