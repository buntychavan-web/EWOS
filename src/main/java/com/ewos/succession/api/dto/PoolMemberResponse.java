package com.ewos.succession.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PoolMemberResponse(
        UUID id,
        UUID poolId,
        UUID employeeId,
        Instant addedAt,
        UUID addedBy,
        Instant removedAt,
        String notes) {}
