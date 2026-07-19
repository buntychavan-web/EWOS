package com.ewos.organization.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateOrganizationUnitRequest(
        UUID unitTypeId,
        UUID parentId,
        @Size(max = 256) String name,
        @Size(max = 1024) String description,
        @Pattern(regexp = "^[A-Z]{2}$", message = "country_code must be ISO-3166-1 alpha-2")
                String countryCode,
        @Size(max = 64) String costCenterCode,
        UUID managerPersonId,
        LocalDate effectiveTo) {}
