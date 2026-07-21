package com.ewos.identity.api;

import com.ewos.identity.api.dto.ChangePasswordRequest;
import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.ResetPasswordRequest;
import com.ewos.identity.api.dto.StatusRequest;
import com.ewos.identity.api.dto.UpdateUserRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.api.dto.UserSearchCriteria;
import com.ewos.identity.application.UserService;
import com.ewos.shared.exception.ApiError;
import com.ewos.shared.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "User Management", description = "Create, update, search and manage user accounts")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SYSTEM_ADMIN')")
    @Operation(summary = "Create a new user (SYSTEM_ADMIN only)")
    @ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "User created",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "Invalid payload or password policy violation",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "403",
                description = "Caller lacks SYSTEM_ADMIN permission",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "409",
                description = "Username or email already in use",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(summary = "Fetch a user by ID")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Found",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public UserResponse getById(@PathVariable UUID id) {
        return userService.getById(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_READ')")
    @Operation(
            summary =
                    "Search users with pagination, sorting, and filtering. "
                            + "Sort with e.g. ?sort=username,asc or ?sort=createdAt,desc.")
    public Page<UserResponse> search(
            @Parameter(description = "Case-insensitive substring match on username")
                    @RequestParam(required = false)
                    String username,
            @Parameter(description = "Case-insensitive substring match on email")
                    @RequestParam(required = false)
                    String email,
            @Parameter(description = "Filter by enabled/disabled") @RequestParam(required = false)
                    Boolean enabled,
            @Parameter(description = "Return only users holding the given role ID")
                    @RequestParam(required = false)
                    UUID roleId,
            @Parameter(description = "Only users created on or after this instant (ISO-8601)")
                    @RequestParam(required = false)
                    Instant createdAfter,
            @Parameter(description = "Only users created on or before this instant (ISO-8601)")
                    @RequestParam(required = false)
                    Instant createdBefore,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return userService.search(
                new UserSearchCriteria(
                        username, email, enabled, roleId, createdAfter, createdBefore),
                pageable);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Operation(summary = "Update mutable profile fields (email, roles)")
    public UserResponse update(
            @PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @Operation(summary = "Enable or disable a user account")
    public UserResponse setStatus(
            @PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
        return userService.setEnabled(id, request.enabled());
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('USER_WRITE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Administratively reset a user's password (bypasses current-password check)")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password reset"),
        @ApiResponse(
                responseCode = "400",
                description = "Password policy violation or reuse",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "404",
                description = "User not found",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void resetPassword(
            @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request.newPassword());
    }

    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Self-service password change. Verifies the current password first.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Password changed"),
        @ApiResponse(
                responseCode = "400",
                description = "Password policy violation or reuse",
                content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(
                responseCode = "401",
                description = "Current password wrong or caller unauthenticated",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void changeMyPassword(
            Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) {
        String subject = authentication == null ? null : authentication.getName();
        if (subject == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated subject is missing");
        }
        UUID actorId;
        try {
            actorId = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated subject is not a valid user id", ex);
        }
        userService.changePassword(actorId, request.currentPassword(), request.newPassword());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_DELETE')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Soft-delete a user",
            description =
                    "Marks the user as deleted (sets deleted_at). Historical audit rows are preserved. "
                            + "The username and email become reusable by a new active user.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "User soft-deleted"),
        @ApiResponse(
                responseCode = "404",
                description = "User not found or already deleted",
                content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public void softDelete(@PathVariable UUID id) {
        userService.softDelete(id);
    }
}
