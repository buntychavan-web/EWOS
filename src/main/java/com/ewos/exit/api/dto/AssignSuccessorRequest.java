package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignSuccessorRequest(@NotNull UUID successorEmployeeId) {}
