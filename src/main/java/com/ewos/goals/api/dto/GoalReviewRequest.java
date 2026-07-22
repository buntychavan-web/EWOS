package com.ewos.goals.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record GoalReviewRequest(@NotNull BigDecimal reviewScore, @Size(max = 4000) String notes) {}
