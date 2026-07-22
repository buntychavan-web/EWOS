package com.ewos.exit.api.dto;

import com.ewos.exit.domain.ClearanceDepartment;
import com.ewos.exit.domain.ClearanceStatus;
import java.time.Instant;
import java.util.UUID;

public record ClearanceResponse(
        UUID id,
        UUID tenantId,
        UUID resignationId,
        ClearanceDepartment department,
        UUID ownerEmployeeId,
        ClearanceStatus status,
        Instant clearedAt,
        UUID clearedBy,
        String notes) {}
