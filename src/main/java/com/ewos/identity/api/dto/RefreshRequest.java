package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh-token exchange request.")
public record RefreshRequest(
        @Schema(description = "Opaque refresh token issued at login.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String refreshToken
) {
}
