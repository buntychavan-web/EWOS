package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.domain.ClientAssignment;
import com.ewos.tenancy.infrastructure.persistence.ClientAssignmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The Chinese Wall. Resolves the current authenticated user's client access from {@link
 * ClientAssignment} rows, checked in real time on every call rather than baked into the JWT — a
 * revoked assignment takes effect on the caller's very next request instead of waiting up to the
 * 15-minute access-token TTL for a stale claim to expire.
 *
 * <p>{@code CLIENT_ADMIN} bypasses per-client scoping entirely, mirroring the existing platform
 * convention of an admin-tier authority short-circuiting fine-grained checks (see how {@code
 * SYSTEM_ADMIN} is seeded with every permission rather than the code special-casing it).
 *
 * <p>A user who holds none of the calling module's permissions never reaches this guard at all
 * (blocked earlier by {@code @PreAuthorize}); a user who holds the permission but has zero {@link
 * ClientAssignment} rows sees zero clients — fail-closed by construction, not by convention.
 */
@Component
public class ClientAccessGuard {

    private final ClientAssignmentRepository repository;

    public ClientAccessGuard(ClientAssignmentRepository repository) {
        this.repository = repository;
    }

    /**
     * True when the caller holds {@code CLIENT_ADMIN} and therefore bypasses per-client scoping.
     */
    public boolean hasUnrestrictedAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("CLIENT_ADMIN"::equals);
    }

    /**
     * The set of client ids the current user may access right now. Only meaningful when {@link
     * #hasUnrestrictedAccess()} is false — callers with unrestricted access should not filter by
     * this set at all.
     */
    public Set<UUID> accessibleClientIds() {
        UUID userId = currentUserId();
        if (userId == null) {
            return Set.of();
        }
        List<ClientAssignment> active = repository.findActiveForUser(userId, LocalDate.now());
        return active.stream().map(a -> a.getClient().getId()).collect(Collectors.toSet());
    }

    /**
     * Throws 403 unless the caller has unrestricted access or an active assignment to this client.
     */
    public void requireAccess(UUID clientId) {
        if (hasUnrestrictedAccess()) {
            return;
        }
        if (!accessibleClientIds().contains(clientId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not authorized for this client");
        }
    }

    /**
     * Same idiom {@code AuditorProvider} uses to resolve the current user: the JWT filter puts the
     * user's UUID string into {@code Authentication#getName()}.
     */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
