package com.ewos.onboarding.application;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.onboarding.api.dto.OnboardingPlanResponse;
import com.ewos.onboarding.api.dto.OnboardingTaskInstanceResponse;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Employee Self-Service over the existing Onboarding module (Sprint 23A) — mirrors {@code
 * LeaveSelfService} (Sprint 3) exactly: every method resolves the caller's own tenant/employee from
 * {@link TenantContext}/{@link EmployeeContext} and delegates entirely to {@link
 * OnboardingPlanService}, which itself scopes each lookup with the self-service-aware {@code
 * ClientAccessGuard} overload — no new business logic lives here.
 */
@Service
public class OnboardingSelfService {

    private final OnboardingPlanService plans;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public OnboardingSelfService(
            OnboardingPlanService plans,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.plans = plans;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    public OnboardingPlanResponse myPlan() {
        UUID tenantId = tenantContext.homeTenantId();
        return plans.forEmployee(tenantId, requireEmployeeId());
    }

    public List<OnboardingTaskInstanceResponse> myTasks() {
        UUID tenantId = tenantContext.homeTenantId();
        return plans.tasksForEmployee(tenantId, requireEmployeeId());
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
