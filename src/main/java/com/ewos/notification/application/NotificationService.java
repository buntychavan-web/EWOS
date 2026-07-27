package com.ewos.notification.application;

import com.ewos.notification.api.dto.NotificationResponse;
import com.ewos.notification.domain.Notification;
import com.ewos.notification.domain.NotificationType;
import com.ewos.notification.infrastructure.persistence.NotificationRepository;
import com.ewos.shared.exception.ApiException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notification inbox. {@link #send} is the delivery primitive other modules call (today:
 * {@code WorkflowNotificationListener}); an in-app row is always written. A future channel (email,
 * push) would be a second implementation of the same call site, not a change to callers — deferred
 * here for lack of an external provider to integrate against in this environment.
 */
@Service
@Transactional
public class NotificationService {

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void send(
            UUID tenantId,
            UUID recipientActorId,
            NotificationType type,
            String title,
            String body,
            String link) {
        if (recipientActorId == null) {
            return;
        }
        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setRecipientActorId(recipientActorId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        repository.save(n);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> mine(UUID tenantId, Pageable pageable) {
        return repository
                .findAllByTenantIdAndRecipientActorIdOrderByCreatedAtDesc(
                        tenantId, requireActor(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID tenantId) {
        return repository.countByTenantIdAndRecipientActorIdAndReadAtIsNull(
                tenantId, requireActor());
    }

    public void markRead(UUID tenantId, UUID id) {
        int updated = repository.markRead(id, tenantId, requireActor());
        if (updated == 0
                && repository
                        .findByIdAndTenantIdAndRecipientActorId(id, tenantId, requireActor())
                        .isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Notification not found");
        }
    }

    // Used via method reference (this::toResponse above) — PMD's UnusedPrivateMethod
    // doesn't resolve method-reference call sites in this version.
    @SuppressWarnings("PMD.UnusedPrivateMethod")
    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getLink(),
                n.getReadAt(),
                n.getCreatedAt());
    }

    private static UUID requireActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user required");
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authenticated user required", e);
        }
    }
}
