package com.ewos.probation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordManagerReviewRequest(@NotBlank @Size(max = 4000) String notes) {}
