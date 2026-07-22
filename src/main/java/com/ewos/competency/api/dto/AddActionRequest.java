package com.ewos.competency.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record AddActionRequest(
        UUID competencyId,
        @NotBlank @Size(max = 2000) String action,
        LocalDate dueOn,
        @Size(max = 2000) String notes) {}
