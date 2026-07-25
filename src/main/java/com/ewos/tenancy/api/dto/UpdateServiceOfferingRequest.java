package com.ewos.tenancy.api.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateServiceOfferingRequest(
        @Size(max = 128) String name,
        @Size(max = 512) String description,
        @Size(max = 64) String category,
        @PositiveOrZero Integer sortOrder,
        Boolean active) {}
