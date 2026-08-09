package com.ewos.notification.api;

import com.ewos.notification.api.dto.NotificationPageResponse;
import com.ewos.notification.application.NotificationService;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27C — Notification Inbox (PRD §4.6). {@code recipientActorId} is always resolved from
 * {@link TenantContext#currentUserId()} (the JWT subject), never accepted from a path/body
 * parameter; both mutating endpoints verify tenant + recipient ownership before touching a row —
 * see {@code NotificationService#markRead}/{@code #dismiss}. Distinct from the pre-existing {@code
 * NotificationController} (still at {@code /api/v1/notifications}, untouched): this is the
 * cursor-paginated, dismiss-capable surface the ESS/MSS self-service API family expects, mirroring
 * {@code /api/v1/leave/self-service} and {@code /api/v1/manager-self-service/*}'s naming
 * convention.
 */
@RestController
@RequestMapping("/api/v1/self-service/notifications")
@Tag(name = "Notification Inbox", description = "The caller's own in-app notifications")
public class NotificationInboxController {

    private static final String HEADER = "Idempotency-Key";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final NotificationService notifications;
    private final TenantContext tenantContext;
    private final IdempotencyService idempotency;

    public NotificationInboxController(
            NotificationService notifications,
            TenantContext tenantContext,
            IdempotencyService idempotency) {
        this.notifications = notifications;
        this.tenantContext = tenantContext;
        this.idempotency = idempotency;
    }

    @GetMapping
    @Operation(summary = "The caller's own notifications, newest first, cursor-paginated")
    public NotificationPageResponse list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        int pageSize = (limit == null || limit <= 0) ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return notifications.myPage(tenantContext.homeTenantId(), unreadOnly, cursor, pageSize);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one of the caller's own notifications as read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        notifications.markRead(tenantContext.homeTenantId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dismiss")
    @Operation(
            summary =
                    "Soft-delete one of the caller's own notifications; dismissed items no longer"
                            + " appear in the inbox or unread count")
    public ResponseEntity<Void> dismiss(
            @PathVariable UUID id,
            @RequestHeader(value = HEADER, required = false) String idempotencyKey) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID actorId =
                tenantContext
                        .currentUserId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.UNAUTHORIZED,
                                                "Authenticated user required"));
        idempotency.execute(
                tenantId,
                actorId,
                "self-service.notifications.dismiss",
                idempotencyKey,
                String.class,
                () -> {
                    notifications.dismiss(tenantId, id);
                    return "OK";
                });
        return ResponseEntity.noContent().build();
    }
}
