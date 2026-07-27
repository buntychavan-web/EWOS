package com.ewos.leave.application;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.dto.BalanceResponse;
import com.ewos.leave.api.dto.CreateLeaveRequestRequest;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.api.dto.LeaveTypeResponse;
import com.ewos.leave.api.dto.SelfLeaveRequestRequest;
import com.ewos.leave.api.dto.SubmitLeaveRequestRequest;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.workflow.application.WorkflowDefinitionService;
import com.ewos.workflow.domain.WorkflowDefinition;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sprint 3 — Employee Self-Service over the existing Leave module. Every method here resolves the
 * caller's own tenant/employee from {@link TenantContext}/{@link EmployeeContext} (mirroring {@code
 * EmployeeController.me()}, Sprint 1.3) and delegates entirely to {@link LeaveRequestService},
 * {@link LeaveBalanceService}, and {@link LeaveTypeService} — no new business logic beyond the
 * ownership check on cancel/submit, which the existing services have no reason to perform
 * themselves since they are also called by admin-tier callers acting on arbitrary employees.
 */
@Service
public class LeaveSelfService {

    private final LeaveRequestService requests;
    private final LeaveBalanceService balances;
    private final LeaveTypeService leaveTypes;
    private final EmployeeRepository employees;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;
    private final WorkflowDefinitionService workflowDefinitions;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public LeaveSelfService(
            LeaveRequestService requests,
            LeaveBalanceService balances,
            LeaveTypeService leaveTypes,
            EmployeeRepository employees,
            TenantContext tenantContext,
            EmployeeContext employeeContext,
            WorkflowDefinitionService workflowDefinitions) {
        this.requests = requests;
        this.balances = balances;
        this.leaveTypes = leaveTypes;
        this.employees = employees;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
        this.workflowDefinitions = workflowDefinitions;
    }

    public List<LeaveTypeResponse> leaveTypes() {
        return leaveTypes.list(tenantContext.homeTenantId());
    }

    public List<LeaveRequestResponse> myRequests() {
        UUID tenantId = tenantContext.homeTenantId();
        return requests.forEmployee(tenantId, requireEmployeeId());
    }

    public LeaveRequestResponse createMyRequest(SelfLeaveRequestRequest request) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        Employee employee = requireEmployee(tenantId, employeeId);
        return requests.create(
                new CreateLeaveRequestRequest(
                        tenantId,
                        employee.getCompanyId(),
                        employeeId,
                        request.leaveTypeId(),
                        request.startDate(),
                        request.endDate(),
                        request.reason()));
    }

    /**
     * Submits the caller's own DRAFT request, resolving the tenant's active leave-approval workflow
     * definition automatically (Sprint 4 audit fix — the caller no longer needs to already know a
     * {@code workflowDefinitionId}; {@link WorkflowDefinitionService#findEffective} picks the
     * highest-version active, currently-effective definition for {@code leave.request}).
     */
    public LeaveRequestResponse submitMyRequest(UUID id) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        requireOwnRequest(tenantId, id, employeeId);
        WorkflowDefinition definition =
                workflowDefinitions.findEffective(tenantId, LeaveRequestService.SUBJECT_TYPE);
        return requests.submit(tenantId, id, new SubmitLeaveRequestRequest(definition.getId()));
    }

    public LeaveRequestResponse cancelMyRequest(UUID id) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        requireOwnRequest(tenantId, id, employeeId);
        return requests.cancel(tenantId, id, false);
    }

    public List<BalanceResponse> myBalances(Integer year) {
        UUID tenantId = tenantContext.homeTenantId();
        int effectiveYear = year != null ? year : LocalDate.now().getYear();
        return balances.balancesForEmployee(tenantId, requireEmployeeId(), effectiveYear);
    }

    /**
     * SUBMITTED requests from the caller's own direct reports (Sprint 4 audit fix — server-side
     * scoping + pagination, replacing the tenant-wide query the My Team screen previously filtered
     * client-side).
     */
    public Page<LeaveRequestResponse> pendingForMyReports(Pageable pageable) {
        UUID tenantId = tenantContext.homeTenantId();
        return requests.pendingForManager(tenantId, requireEmployeeId(), pageable);
    }

    private void requireOwnRequest(UUID tenantId, UUID id, UUID employeeId) {
        LeaveRequestResponse existing = requests.getById(tenantId, id);
        if (!employeeId.equals(existing.employeeId())) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN, "This leave request does not belong to you");
        }
    }

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found"));
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
