package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Administrative password reset. Bypasses current-password check.")
public record ResetPasswordRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String newPassword) {}
