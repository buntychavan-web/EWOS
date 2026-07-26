package com.ewos.employee.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

/**
 * Creates a brand-new {@code User} and links it to an {@code Employee} in one call. Mirrors {@code
 * CreateUserRequest}'s required fields exactly — this codebase has no auto-generated-temp-password
 * mechanism, so the admin supplies the initial password, same as user creation already works.
 */
public record ProvisionUserRequest(
        @NotBlank @Size(min = 3, max = 150) String username,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password,
        Set<UUID> roleIds,
        Boolean enabled,
        @Size(max = 500) String reason) {}
