package com.ewos.exit.application;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helper for services that need the current authenticated user id. */
final class ExitSecurity {

    private ExitSecurity() {}

    static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
