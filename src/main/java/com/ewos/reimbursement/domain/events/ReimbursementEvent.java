package com.ewos.reimbursement.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event covering reimbursement claim lifecycle changes. Published on {@code AFTER_COMMIT}.
 */
public record ReimbursementEvent(
        ReimbursementEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID employeeId,
        UUID claimId,
        UUID actorId,
        Instant occurredAt) {}
