package com.ewos.employee.api.dto;

import com.ewos.employee.domain.EmployeeIdentityLinkAction;
import java.time.Instant;
import java.util.UUID;

public record EmployeeIdentityHistoryResponse(
        UUID id,
        EmployeeIdentityLinkAction action,
        UUID previousUserId,
        UUID newUserId,
        String reason,
        UUID actorId,
        Instant occurredAt) {}
