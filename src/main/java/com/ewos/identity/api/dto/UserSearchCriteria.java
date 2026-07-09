package com.ewos.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Filter parameters for {@code GET /api/v1/users}. Any field left null is ignored. All string
 * filters are case-insensitive substring matches.
 */
public record UserSearchCriteria(
        String username,
        String email,
        Boolean enabled,
        UUID roleId,
        Instant createdAfter,
        Instant createdBefore) {}
