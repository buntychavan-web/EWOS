package com.ewos.notification.api.dto;

import com.ewos.notification.domain.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpsertNotificationTemplateRequest(
        @NotNull NotificationType type,
        @NotBlank @Size(max = 256) String titleTemplate,
        @Size(max = 2048) String bodyTemplate,
        boolean active) {}
