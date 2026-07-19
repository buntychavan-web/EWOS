package com.ewos.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreateOrganizationUnitRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotNull UUID unitTypeId,
        UUID parentId,
        @NotBlank
                @Size(max = 64)
                @Pattern(
                        regexp = "^[A-Za-z0-9][A-Za-z0-9._-]*$",
                        message = "code must be alphanumeric with . _ - allowed")
                String code,
        @NotBlank @Size(max = 256) String name,
        @Size(max = 1024) String description,
        @Pattern(regexp = "^[A-Z]{2}$", message = "country_code must be ISO-3166-1 alpha-2")
                String countryCode,
        @Size(max = 64) String costCenterCode,
        UUID managerPersonId,
        @NotNull LocalDate effectiveFrom,
        LocalDate effectiveTo) {}
