package com.ewos.notification.api.dto;

import com.ewos.notification.domain.NotificationType;
import java.util.UUID;

public record NotificationTemplateResponse(
        UUID id,
        UUID tenantId,
        NotificationType type,
        String titleTemplate,
        String bodyTemplate,
        boolean active) {}
