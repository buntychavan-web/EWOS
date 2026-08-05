package com.ewos.exit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateExitChecklistTemplateRequest(
        @NotNull UUID companyId,
        UUID orgUnitId,
        @NotBlank @Size(max = 200) String name,
        @NotEmpty @Valid List<ChecklistItemSpec> items) {}
