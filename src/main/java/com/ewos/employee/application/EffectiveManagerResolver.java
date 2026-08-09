package com.ewos.employee.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.audit.CrossEmployeeAccessLogService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.workflow.application.WorkflowDelegationService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Sprint 27B/27C — resolves which manager's data an MSS endpoint should act on: the caller's own,
 * or (via {@code actingForEmployeeId}) a peer who has actively delegated to them. Extracted from
 * {@code ManagerApprovalsService} (Sprint 27B) so {@code MssTeamService} and {@code
 * MssDashboardService} (Sprint 27C) reuse the identical delegation-check + enumeration-protection +
 * audit-logging logic rather than re-implementing it — same mechanism, same behavior, everywhere
 * {@code actingForEmployeeId} appears.
 */
@Component
public class EffectiveManagerResolver {

    private static final String ACTION = "MSS_ACTING_FOR";

    private final EmployeeRepository employees;
    private final TenantContext tenantContext;
    private final WorkflowDelegationService delegations;
    private final CrossEmployeeAccessLogService accessLog;

    public EffectiveManagerResolver(
            EmployeeRepository employees,
            TenantContext tenantContext,
            WorkflowDelegationService delegations,
            CrossEmployeeAccessLogService accessLog) {
        this.employees = employees;
        this.tenantContext = tenantContext;
        this.delegations = delegations;
        this.accessLog = accessLog;
    }

    /**
     * Uniform 404 whether {@code actingForEmployeeId} doesn't exist, belongs to another tenant, or
     * simply isn't an active delegator to the caller — enumeration protection per PRD §17. Logs
     * every delegated access (granted or denied) to the cross-employee access log.
     */
    public UUID resolve(UUID tenantId, UUID callerEmployeeId, UUID actingForEmployeeId) {
        return resolve(tenantId, callerEmployeeId, actingForEmployeeId, ACTION, "Not found");
    }

    public UUID resolve(
            UUID tenantId,
            UUID callerEmployeeId,
            UUID actingForEmployeeId,
            String action,
            String notFoundMessage) {
        if (actingForEmployeeId == null || actingForEmployeeId.equals(callerEmployeeId)) {
            return callerEmployeeId;
        }
        Employee delegator =
                employees.findByIdAndTenantId(actingForEmployeeId, tenantId).orElse(null);
        UUID callerUserId = tenantContext.currentUserId().orElse(null);
        boolean active =
                delegator != null
                        && delegator.getUserId() != null
                        && callerUserId != null
                        && delegations.isActiveDelegateOf(
                                tenantId, delegator.getUserId(), callerUserId);
        if (!active) {
            accessLog.logDenied(
                    tenantId,
                    callerEmployeeId,
                    actingForEmployeeId,
                    action,
                    "no active delegation");
            throw new ApiException(HttpStatus.NOT_FOUND, notFoundMessage);
        }
        accessLog.logGranted(tenantId, callerEmployeeId, actingForEmployeeId, action);
        return actingForEmployeeId;
    }
}
