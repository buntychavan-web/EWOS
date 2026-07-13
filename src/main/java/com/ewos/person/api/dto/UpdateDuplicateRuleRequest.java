package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Toggle or reweight a duplicate-detection rule")
public record UpdateDuplicateRuleRequest(
        @NotNull Boolean enabled, @NotNull @Min(1) @Max(100) Integer weight) {}
