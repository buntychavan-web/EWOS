package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Update the name / display sequence of an organization level")
public record UpdateOrganizationLevelRequest(
        @NotBlank @Size(max = 255) String name, @NotNull @Positive Integer displaySequence) {}
