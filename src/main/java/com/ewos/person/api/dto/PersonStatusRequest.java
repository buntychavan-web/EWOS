package com.ewos.person.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Activate / deactivate a Person")
public record PersonStatusRequest(@NotNull Boolean active) {}
