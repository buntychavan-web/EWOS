package com.ewos.performance.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ReviewerAssessmentRequest(
        @NotNull BigDecimal rating, @Size(max = 4000) String comments) {}
