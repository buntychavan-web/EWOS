package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "Retire an inheritance override by setting its effectiveTo")
public record RetireOverrideRequest(@NotNull LocalDate effectiveTo) {}
