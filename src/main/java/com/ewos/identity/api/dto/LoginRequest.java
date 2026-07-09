package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials submitted to obtain a fresh token pair.")
public record LoginRequest(
        @Schema(example = "admin", requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank
                String username,
        @Schema(example = "ChangeMe!Admin123", requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                String password) {}
