package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record StartGardenLeaveRequest(@NotNull LocalDate startDate, @NotNull LocalDate endDate) {}
