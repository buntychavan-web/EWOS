package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Enable or disable a user account.")
public record StatusRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Boolean enabled
) {
}
