package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record ApplyBuyoutRequest(@NotNull @Positive Integer buyoutDays, BigDecimal buyoutAmount) {}
