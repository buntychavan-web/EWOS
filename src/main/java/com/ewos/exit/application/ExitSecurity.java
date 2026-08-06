package com.ewos.exit.application;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helper for services that need the current authenticated user id. */
final class ExitSecurity {

    private ExitSecurity() {}

    /**
     * Resolves the current actor's id from the security context. Returns {@code null} only when
     * there is genuinely no authenticated principal (e.g. a system-initiated action) — callers rely
     * on that to leave audit fields like {@code submittedBy}/{@code acceptedBy} null. If a
     * principal IS present but its name isn't a valid UUID, that's not a legitimate "no actor" case
     * — it's a broken invariant (this system's auth always issues UUID principal names), so this
     * fails loudly instead of silently discarding the actor and leaving the audit trail looking
     * unattributed.
     */
    static UUID currentActor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Authenticated principal name is not a valid UUID: " + auth.getName(), e);
        }
    }
}
