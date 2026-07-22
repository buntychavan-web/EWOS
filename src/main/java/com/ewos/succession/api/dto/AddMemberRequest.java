package com.ewos.succession.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AddMemberRequest(@NotNull UUID employeeId, @Size(max = 2000) String notes) {}
