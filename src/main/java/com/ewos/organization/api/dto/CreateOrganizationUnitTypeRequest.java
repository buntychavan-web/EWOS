package com.ewos.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateOrganizationUnitTypeRequest(
        @NotNull UUID tenantId,
        @NotBlank
                @Size(max = 64)
                @Pattern(
                        regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
                        message = "code must be alphanumeric with . _ - allowed")
                String code,
        @NotBlank @Size(max = 128) String name,
        @Size(max = 512) String description,
        @PositiveOrZero Integer sortOrder,
        Boolean active) {}
