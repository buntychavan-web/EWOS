package com.ewos.exit.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateKtItemRequest(
        @NotBlank String topic, String description, UUID transferredTo, String notes) {}
