package com.ewos.goals.application;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.goals.api.dto.GoalProgressResponse;
import com.ewos.goals.api.dto.GoalResponse;
import com.ewos.goals.api.dto.ProgressUpdateRequest;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sprint 24D — Employee Self-Service over Goals. Mirrors {@code PerformanceSelfService} (Sprint
 * 24A) exactly: authenticated-only, resolves the caller's own tenant/employee, and delegates to the
 * existing {@link GoalService}. {@code byEmployee} is already safely scoped by {@link GoalService}
 * (it only ever returns the named employee's own goals), but {@code recordProgress} and {@code
 * submitForReview} take a bare goal id with no ownership check at the {@code GoalService} layer
 * (that layer only enforces company-level access, matching every other admin-tier Goals endpoint) —
 * this class adds the missing object-level ownership check at the self-service boundary so an
 * ordinary employee holding {@code GOAL_UPDATE_PROGRESS} can only ever act on their own goals,
 * never a colleague's.
 */
@Service
public class GoalSelfService {

    private final GoalService goals;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public GoalSelfService(
            GoalService goals, TenantContext tenantContext, EmployeeContext employeeContext) {
        this.goals = goals;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    public List<GoalResponse> myGoals() {
        return goals.byEmployee(tenantContext.homeTenantId(), requireEmployeeId());
    }

    public GoalProgressResponse recordMyProgress(UUID id, ProgressUpdateRequest req) {
        UUID tenantId = tenantContext.homeTenantId();
        requireOwnership(tenantId, id);
        return goals.recordProgress(tenantId, id, req);
    }

    public GoalResponse submitMyGoalForReview(UUID id) {
        UUID tenantId = tenantContext.homeTenantId();
        requireOwnership(tenantId, id);
        return goals.submitForReview(tenantId, id);
    }

    private void requireOwnership(UUID tenantId, UUID goalId) {
        UUID employeeId = requireEmployeeId();
        GoalResponse g = goals.getById(tenantId, goalId);
        if (g.employeeId() == null || !g.employeeId().equals(employeeId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You may only act on your own goals");
        }
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
