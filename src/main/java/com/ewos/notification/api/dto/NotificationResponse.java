package com.ewos.notification.api.dto;

import com.ewos.notification.domain.NotificationType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        String link,
        Instant readAt,
        Instant createdAt) {}
