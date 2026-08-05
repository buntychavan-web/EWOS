package com.ewos.exit.api.dto;

import com.ewos.exit.domain.KtItemType;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/**
 * {@code itemType} defaults to {@link KtItemType#TASK} when omitted, for backward compatibility.
 */
public record CreateKtItemRequest(
        KtItemType itemType,
        @NotBlank String topic,
        String description,
        UUID transferredTo,
        String notes) {}
