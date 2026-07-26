package com.ewos.identity.api;

import com.ewos.identity.api.dto.LoginRequest;
import com.ewos.identity.api.dto.MeResponse;
import com.ewos.identity.api.dto.RefreshRequest;
import com.ewos.identity.api.dto.TokenResponse;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.application.AuthenticationService;
import com.ewos.identity.application.UserService;
import com.ewos.shared.exception.ApiError;
import com.ewos.shared.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** Must match {@code JwtAuthenticationFilter.TENANT_ID_REQUEST_ATTRIBUTE} exactly. */
    private static final String TENANT_ID_REQUEST_ATTRIBUTE = "com.ewos.tenancy.currentTenantId";

    /** Must match {@code JwtAuthenticationFilter.EMPLOYEE_ID_REQUEST_ATTRIBUTE} exactly. */
    private static final String EMPLOYEE_ID_REQUEST_ATTRIBUTE = "com.ewos.employee.currentEmployeeId";

    private final AuthenticationService authenticationService;
    private final UserService userService;

    public AuthController(AuthenticationService authenticationService, UserService userService) {
        this.authenticationService = authenticationService;
        this.userService = userService;
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

    @GetMapping("/me")
    @SecurityRequirement(name = "bearer-jwt")
    @Operation(
            summary = "Current authenticated identity snapshot",
            description =
                    "User + tenant (if resolved) + employee (if linked — see Sprint 1.3's Employee<->User"
                            + " link). tenantId/employeeId are the JWT's own claims, not re-queried per"
                            + " call.")
    public MeResponse me(Authentication authentication, HttpServletRequest request) {
        if (authentication == null || authentication.getName() == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        UUID userId = UUID.fromString(authentication.getName());
        UserResponse user = userService.getById(userId);
        return new MeResponse(
                user.id(),
                user.username(),
                user.email(),
                user.roles(),
                uuidAttribute(request, TENANT_ID_REQUEST_ATTRIBUTE),
                uuidAttribute(request, EMPLOYEE_ID_REQUEST_ATTRIBUTE));
    }

    private static UUID uuidAttribute(HttpServletRequest request, String attributeName) {
        Object value = request.getAttribute(attributeName);
        return value instanceof UUID uuid ? uuid : null;
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
