package com.ewos.shared.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolves the current auditor (user UUID) from the Spring Security context.
 *
 * <p>The JWT authentication filter puts the user's UUID string into {@link
 * Authentication#getName()}, so we parse it here. This provider returns {@link Optional#empty()} in
 * any of the following situations — JPA responds by leaving {@code created_by} / {@code updated_by}
 * NULL on the row:
 *
 * <ol>
 *   <li><b>No security context</b> — writes performed before any request lands (bootstrap admin
 *       creation in {@code IdentityBootstrap}, Flyway seeds, {@code @Scheduled} jobs).
 *   <li><b>Anonymous request</b> — the JWT filter did not authenticate the caller. This should not
 *       happen for endpoints under {@code /api/**} because {@link
 *       com.ewos.identity.infrastructure.security.SecurityConfig} requires authentication on every
 *       non-public path; it would only arise if a permit-all endpoint decided to persist a row.
 *   <li><b>Non-UUID subject</b> — defensive branch. The JWT is issued by this application so the
 *       subject is always a UUID, but if a future flow (e.g. service-to-service tokens) uses a
 *       different subject format, we fall through cleanly rather than break the write.
 * </ol>
 *
 * <p>Consumers must not read {@code created_by} / {@code updated_by} as "always non-null" — the
 * bootstrap admin row, seed data, and any purge / scheduled writes will legitimately have NULL
 * here. See {@code docs/operations/auditor-and-actuator.md} for the operational implications.
 */
@Component
public class AuditorProvider implements AuditorAware<UUID> {

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(name));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
