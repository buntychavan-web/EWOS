package com.ewos.competency.application;

import com.ewos.competency.api.dto.AssessmentResponse;
import com.ewos.competency.api.dto.CompetencyResponse;
import com.ewos.competency.api.dto.EmployeeCompetencyResponse;
import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sprint 24D — Employee Self-Service over Competencies. Mirrors {@code PerformanceSelfService}
 * (Sprint 24A) and {@code LeaveSelfService} (Sprint 3): authenticated-only, resolves the caller's
 * own tenant/employee, and delegates to the existing {@link EmployeeCompetencyService} and {@link
 * CompetencyService}. {@code forEmployee}/{@code assessmentsFor}/{@code listActive} are already
 * safely scoped by those services (each only ever returns data for the employee/company id it is
 * given), so — unlike Goals — no extra ownership check is needed here: this class only ever passes
 * the caller's own resolved employee/company id, never an id from request input. Read-only by
 * design: recording a self-assessment or upserting a level remains an admin/manager action, not
 * something an employee does to their own record.
 */
@Service
public class CompetencySelfService {

    private final EmployeeCompetencyService employeeCompetencies;
    private final CompetencyService competencies;
    private final EmployeeRepository employees;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public CompetencySelfService(
            EmployeeCompetencyService employeeCompetencies,
            CompetencyService competencies,
            EmployeeRepository employees,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.employeeCompetencies = employeeCompetencies;
        this.competencies = competencies;
        this.employees = employees;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    public List<EmployeeCompetencyResponse> myCompetencies() {
        UUID tenantId = tenantContext.homeTenantId();
        return employeeCompetencies.forEmployee(tenantId, requireEmployeeId());
    }

    public List<AssessmentResponse> myAssessments() {
        UUID tenantId = tenantContext.homeTenantId();
        return employeeCompetencies.assessmentsFor(tenantId, requireEmployeeId());
    }

    public List<CompetencyResponse> competencyCatalog() {
        UUID tenantId = tenantContext.homeTenantId();
        Employee employee = requireEmployee(tenantId, requireEmployeeId());
        return competencies.listActive(tenantId, employee.getCompanyId());
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
