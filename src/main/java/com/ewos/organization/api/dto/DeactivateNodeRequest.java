package com.ewos.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Schema(
        description =
                "Deactivate a node — closes its effective window and writes a DEACTIVATED version")
public record DeactivateNodeRequest(
        @NotNull LocalDate effectiveTo, @Size(max = 500) String notes) {}
