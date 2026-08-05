package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ClearanceDepartment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChecklistItemSpec(
        @NotNull ClearanceDepartment department,
        @NotBlank @Size(max = 200) String itemName,
        Integer sortOrder) {}
