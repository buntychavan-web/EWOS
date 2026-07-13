package com.ewos.company.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Closes an effective-dated record by setting its {@code effective_to}. */
public record RetireRequest(@NotNull LocalDate effectiveTo) {}
