package com.ewos.offer.api.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ConfirmJoiningRequest(UUID employeeId, @Size(max = 4000) String notes) {}
