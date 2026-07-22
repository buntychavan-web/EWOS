package com.ewos.learning.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AssessmentRequest(@NotNull BigDecimal score, @NotNull boolean passed) {}
