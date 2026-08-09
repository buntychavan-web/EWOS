package com.ewos.employee.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Sprint 27C — {@code PATCH /api/v1/self-service/me}. Every field is optional; an omitted field
 * (null) leaves the current value unchanged. {@code workEmail} and {@code displayName} are
 * deliberately absent — both are HR-admin-only per the PRD and are never accepted here.
 */
public record EssProfileUpdateRequest(
        @Email @Size(max = 320) String personalEmail,
        @Pattern(
                        regexp = "^\\+?[0-9()\\-\\s]{7,32}$",
                        message = "phone must be a valid phone number")
                String phone,
        @Size(max = 200) String emergencyContactName,
        @Pattern(
                        regexp = "^\\+?[0-9()\\-\\s]{7,32}$",
                        message = "phone must be a valid phone number")
                @Size(max = 50)
                String emergencyContactPhone,
        @Size(max = 500) String avatarStorageUri) {}
