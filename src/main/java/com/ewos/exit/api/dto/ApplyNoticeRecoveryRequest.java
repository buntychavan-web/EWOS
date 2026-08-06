package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ApplyNoticeRecoveryRequest(@NotNull @PositiveOrZero BigDecimal amount) {}
