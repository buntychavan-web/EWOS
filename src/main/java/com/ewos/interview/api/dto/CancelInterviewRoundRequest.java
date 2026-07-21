package com.ewos.interview.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelInterviewRoundRequest(@NotBlank @Size(max = 4000) String reason) {}
