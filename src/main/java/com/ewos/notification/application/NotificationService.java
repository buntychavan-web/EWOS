package com.ewos.notification.application;

import com.ewos.notification.api.dto.NotificationResponse;
import com.ewos.notification.domain.Notification;
import com.ewos.notification.domain.NotificationTemplate;
import com.ewos.notification.domain.NotificationType;
import com.ewos.notification.infrastructure.persistence.NotificationRepository;
import com.ewos.notification.infrastructure.persistence.NotificationTemplateRepository;
import com.ewos.shared.exception.ApiException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * In-app notification inbox. {@link #send} is the delivery primitive other modules call; an in-app
 * row is always written, and — since Sprint 24E — the email channel ({@link
 * EmailNotificationSender}) fires alongside it whenever configured. Callers always pass a sane
 * default title/body; a {@link NotificationTemplate} row (tenant-specific, falling back to a
 * platform-wide default) overrides that text when one is configured, so wording is editable without
 * a code change without forcing every call site to look one up itself.
 */
@Service
@Transactional
public class NotificationService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final NotificationRepository repository;
    private final NotificationTemplateRepository templates;
    private final Optional<EmailNotificationSender> emailSender;

    public NotificationService(
            NotificationRepository repository,
            NotificationTemplateRepository templates,
            Optional<EmailNotificationSender> emailSender) {
        this.repository = repository;
        this.templates = templates;
        this.emailSender = emailSender;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(
            UUID tenantId,
            UUID recipientActorId,
            NotificationType type,
            String title,
            String body,
            String link) {
        send(tenantId, recipientActorId, type, title, body, link, Map.of());
    }

    /**
     * As {@link #send(java.util.UUID, java.util.UUID, NotificationType, String, String, String)},
     * but {@code defaultTitle}/{@code defaultBody} may contain {@code {{key}}} placeholders
     * resolved against {@code params}, and are themselves only used when no {@link
     * NotificationTemplate} is configured for {@code type}.
     *
     * <p>{@code REQUIRES_NEW} is deliberate, not decorative: every caller of {@code send} is a
     * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} handler
     * (Goal/Competency/Performance notification listeners) invoked after the triggering transaction
     * has already committed but before Spring has unbound that transaction's resources from the
     * thread. Under the class's previous default propagation ({@code REQUIRED}), that
     * stale-but-still-bound resource caused this method to "participate" in the already-finished
     * transaction instead of opening a real one — the {@link Notification} got persisted into a
     * persistence context that was never flushed, so the insert silently never reached the database
     * (no exception, no log, just a missing row). {@code REQUIRES_NEW} forces a genuinely fresh
     * transaction every time, so the insert is guaranteed to flush and commit for real.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(
            UUID tenantId,
            UUID recipientActorId,
            NotificationType type,
            String defaultTitle,
            String defaultBody,
            String link,
            Map<String, String> params) {
        if (recipientActorId == null) {
            return;
        }
        String title = applyParams(resolveTitle(tenantId, type, defaultTitle), params);
        String body = applyParams(resolveBody(tenantId, type, defaultBody), params);

        Notification n = new Notification();
        n.setTenantId(tenantId);
        n.setRecipientActorId(recipientActorId);
        n.setType(type);
        n.setTitle(title);
        n.setBody(body);
        n.setLink(link);
        repository.save(n);

        emailSender.ifPresent(sender -> sender.send(tenantId, recipientActorId, type, title, body));
    }

    private String resolveTitle(UUID tenantId, NotificationType type, String fallback) {
        return template(tenantId, type)
                .map(NotificationTemplate::getTitleTemplate)
                .orElse(fallback);
    }

    private String resolveBody(UUID tenantId, NotificationType type, String fallback) {
        return template(tenantId, type).map(NotificationTemplate::getBodyTemplate).orElse(fallback);
    }

    private Optional<NotificationTemplate> template(UUID tenantId, NotificationType type) {
        return templates
                .findByTenantIdAndTypeAndActiveTrue(tenantId, type)
                .or(() -> templates.findByTenantIdIsNullAndTypeAndActiveTrue(type));
    }

    private static String applyParams(String text, Map<String, String> params) {
        if (text == null || params.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String value = params.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
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
