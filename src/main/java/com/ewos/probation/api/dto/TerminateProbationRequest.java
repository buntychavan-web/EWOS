package com.ewos.probation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TerminateProbationRequest(@NotBlank @Size(max = 4000) String reason) {}
