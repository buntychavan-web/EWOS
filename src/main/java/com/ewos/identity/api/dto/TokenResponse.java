package com.ewos.identity.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access + refresh token pair.")
public record TokenResponse(
        @Schema(description = "Short-lived JWT access token.") String accessToken,
        @Schema(description = "Long-lived opaque refresh token (rotate on each use).") String refreshToken,
        @Schema(description = "Token scheme.", example = "Bearer") String tokenType,
        @Schema(description = "Access-token lifetime in seconds.") long expiresIn
) {
}
