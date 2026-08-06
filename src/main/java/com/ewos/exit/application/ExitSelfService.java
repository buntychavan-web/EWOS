package com.ewos.exit.application;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.exit.api.dto.CreateResignationRequest;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.api.dto.SelfResignationRequest;
import com.ewos.exit.domain.ResignationType;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sprint 26 — Employee Self-Service over the existing Exit module. Every method resolves the
 * caller's own tenant/employee from {@link TenantContext}/{@link EmployeeContext} (mirroring {@code
 * LeaveSelfService} exactly) and delegates to {@link ExitService} — no new business logic beyond
 * the ownership check on withdraw, which {@link ExitService} has no reason to perform itself since
 * it is also called by HR/admin-tier callers acting on arbitrary employees.
 */
@Service
public class ExitSelfService {

    private final ExitService exit;
    private final EmployeeRepository employees;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public ExitSelfService(
            ExitService exit,
            EmployeeRepository employees,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.exit = exit;
        this.employees = employees;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    public ResignationResponse submitMyResignation(SelfResignationRequest request) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        Employee employee = requireEmployee(tenantId, employeeId);
        return exit.submitSelf(
                tenantId,
                new CreateResignationRequest(
                        employee.getCompanyId(),
                        employeeId,
                        ResignationType.SELF_RESIGNATION,
                        request.intendedLastDay(),
                        request.reason(),
                        request.noticePeriodDays()));
    }

    public List<ResignationResponse> myResignations() {
        UUID tenantId = tenantContext.homeTenantId();
        return exit.resignationsForEmployee(tenantId, requireEmployeeId());
    }

    public ResignationResponse withdrawMyResignation(UUID id) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId = requireEmployeeId();
        requireOwnResignation(tenantId, id, employeeId);
        return exit.withdraw(tenantId, id);
    }

    private void requireOwnResignation(UUID tenantId, UUID id, UUID employeeId) {
        ResignationResponse existing = exit.getResignation(tenantId, id);
        if (!employeeId.equals(existing.employeeId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This resignation does not belong to you");
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
