package com.ewos.organization.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateOrganizationUnitTypeRequest(
        @Size(max = 128) String name,
        @Size(max = 512) String description,
        @PositiveOrZero Integer sortOrder,
        Boolean active) {}
