package com.ewos.probation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ExtendProbationRequest(
        @NotNull LocalDate newEndDate, @NotBlank @Size(max = 2000) String reason) {}
