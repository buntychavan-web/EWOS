package com.ewos.payroll.domain.events;

import java.util.UUID;

/**
 * Published by {@code PayrollApprovalService} after a transactional state change so notification
 * delivery can happen at {@code AFTER_COMMIT} — the same convention every other notification
 * listener in this codebase relies on (see {@code NotificationService.send}'s Javadoc on why {@code
 * REQUIRES_NEW} depends on this timing).
 *
 * <p>For {@code SUBMITTED}/{@code LEVEL_ADVANCED}, {@code levelNumber} and {@code approverRoleCode}
 * identify who to notify (resolved via {@code ApproverResolver} at delivery time, not snapshotted
 * here, so membership changes between submission and delivery are reflected correctly). For {@code
 * FULLY_APPROVED}/{@code REJECTED}, {@code preparerId} is the recipient.
 */
public record PayrollApprovalEvent(
        PayrollApprovalEventType eventType,
        UUID tenantId,
        UUID companyId,
        UUID payrollRunId,
        UUID approvalRequestId,
        Integer levelNumber,
        String approverRoleCode,
        UUID preparerId,
        UUID decidedBy,
        String comments) {}
