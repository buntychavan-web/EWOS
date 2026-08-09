package com.ewos.notification.api.dto;

import java.util.List;

/**
 * Sprint 27C — cursor-paginated page of {@link NotificationResponse} for {@code
 * NotificationInboxController}. {@code nextCursor} is {@code null} once the caller has reached the
 * end of the (newest-first) result set.
 */
public record NotificationPageResponse(List<NotificationResponse> items, String nextCursor) {}
