package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(description = "Rename an organization node — recorded as a RENAMED version")
public record RenameNodeRequest(
        @NotBlank @Size(max = 255) String newName,
        @NotNull LocalDate effectiveFrom,
        @Size(max = 500) String notes) {}
