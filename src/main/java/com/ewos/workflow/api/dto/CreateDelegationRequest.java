package com.ewos.workflow.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateDelegationRequest(
        @NotNull UUID delegateActorId,
        @Size(max = 64) String roleCode,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt,
        @Size(max = 1024) String notes) {}
