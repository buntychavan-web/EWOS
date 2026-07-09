package com.ewos.common.persistence;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the current auditor (user UUID) from the Spring Security context.
 * The JWT authentication filter puts the user's UUID string into
 * {@link Authentication#getName()}, so we parse it here.
 * Returns empty for anonymous / system contexts — JPA leaves the column null.
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
