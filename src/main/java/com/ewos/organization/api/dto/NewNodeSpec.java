package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "New node spec for a split operation")
public record NewNodeSpec(
        @NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 255) String name) {}
