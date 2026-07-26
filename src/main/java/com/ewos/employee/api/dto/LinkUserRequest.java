package com.ewos.employee.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record LinkUserRequest(@NotNull UUID userId, @Size(max = 500) String reason) {}
