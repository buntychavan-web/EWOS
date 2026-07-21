package com.ewos.interview.application;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/** Small helper for services that need the current authenticated user id. */
final class InterviewSecurity {

    private InterviewSecurity() {}

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
