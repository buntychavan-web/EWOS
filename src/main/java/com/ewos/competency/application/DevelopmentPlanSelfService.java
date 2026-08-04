package com.ewos.competency.application;

import com.ewos.competency.api.dto.ActionResponse;
import com.ewos.competency.api.dto.PlanResponse;
import com.ewos.employee.application.EmployeeContext;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sprint 24D — Employee Self-Service over Development Plans. Mirrors {@code GoalSelfService}
 * exactly: authenticated-only, resolves the caller's own tenant/employee, and delegates to the
 * existing {@link DevelopmentPlanService}. {@code forEmployee} is already safely scoped (it only
 * ever returns the named employee's own plans), but {@code actionsFor} and {@code completeAction}
 * take a bare plan/action id with no ownership check at the {@code DevelopmentPlanService} layer
 * (that layer only enforces company-level access, matching every other admin-tier Competency
 * endpoint) — this class adds the missing object-level ownership check at the self-service boundary
 * so an ordinary employee holding {@code COMPETENCY_ACTION_COMPLETE} can only ever act on their own
 * development plan, never a colleague's.
 */
@Service
public class DevelopmentPlanSelfService {

    private final DevelopmentPlanService plans;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public DevelopmentPlanSelfService(
            DevelopmentPlanService plans,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.plans = plans;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    public List<PlanResponse> myPlans() {
        return plans.forEmployee(tenantContext.homeTenantId(), requireEmployeeId());
    }

    public List<ActionResponse> myPlanActions(UUID planId) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        PlanResponse p = plans.getById(tenantId, planId);
        if (p.employeeId() == null || !p.employeeId().equals(employeeId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "You may only view your own development plan");
        }
        return plans.actionsFor(tenantId, planId);
    }

    public ActionResponse completeMyAction(UUID actionId) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        UUID owner = plans.employeeIdForAction(tenantId, actionId);
        if (owner == null || !owner.equals(employeeId)) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "You may only act on your own development plan actions");
        }
        return plans.completeAction(tenantId, actionId);
    }

    private UUID requireEmployeeId() {
        return employeeContext
                .currentEmployeeId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No employee record is linked to your account"));
    }
}
