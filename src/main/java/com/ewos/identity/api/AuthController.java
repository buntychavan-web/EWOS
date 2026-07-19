package com.ewos.identity.api;

import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.RefreshRequest;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.application.AuthenticationService;
import com.ewos.shared.exception.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Login, refresh, and logout")
@SecurityRequirements
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate a user and issue a token pair")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Authentication succeeded",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Malformed request",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Invalid credentials",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Account disabled or locked",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public TokenResponse login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authenticationService.login(
                request.username(),
                request.password(),
                extractClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate a refresh token for a new access + refresh pair")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Rotation succeeded",
                content = @Content(schema = @Schema(implementation = TokenResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Malformed request",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Refresh token expired, revoked, or unknown",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public TokenResponse refresh(
            @Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authenticationService.refresh(
                request.refreshToken(),
                extractClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Revoke a refresh token",
            description =
                    "Idempotent — returns 204 for unknown or already-revoked tokens. "
                            + "The associated access token remains valid until it expires; clients should discard it.")
    public void logout(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        authenticationService.logout(
                request.refreshToken(),
                extractClientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));
    }

    private static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
