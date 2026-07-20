package com.ewos.workflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record StateDefinitionSpec(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 128) String name,
        boolean initial,
        boolean terminal,
        @PositiveOrZero Integer sortOrder,
        @Positive Integer slaHours) {}
