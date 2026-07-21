package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.payroll.api.dto.CreateEmployeeCostAllocationRequest;
import com.ewos.payroll.api.dto.EmployeeCostAllocationResponse;
import com.ewos.payroll.domain.BusinessUnit;
import com.ewos.payroll.domain.CostCentre;
import com.ewos.payroll.domain.EmployeeCostAllocation;
import com.ewos.payroll.infrastructure.persistence.BusinessUnitRepository;
import com.ewos.payroll.infrastructure.persistence.CostCentreRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeCostAllocationRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-employee cost / department allocation. */
@Service
@Transactional
public class EmployeeCostAllocationService {

    private final EmployeeCostAllocationRepository repository;
    private final EmployeeRepository employees;
    private final CostCentreRepository costCentres;
    private final BusinessUnitRepository businessUnits;
    private final OrganizationUnitRepository orgUnits;

    public EmployeeCostAllocationService(
            EmployeeCostAllocationRepository repository,
            EmployeeRepository employees,
            CostCentreRepository costCentres,
            BusinessUnitRepository businessUnits,
            OrganizationUnitRepository orgUnits) {
        this.repository = repository;
        this.employees = employees;
        this.costCentres = costCentres;
        this.businessUnits = businessUnits;
        this.orgUnits = orgUnits;
    }

    public EmployeeCostAllocationResponse create(CreateEmployeeCostAllocationRequest r) {
        Employee employee =
                employees
                        .findByIdAndTenantId(r.employeeId(), r.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(r.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }
        EmployeeCostAllocation a = new EmployeeCostAllocation();
        a.setTenantId(r.tenantId());
        a.setCompanyId(r.companyId());
        a.setEmployee(employee);
        a.setPercentage(r.percentage());
        a.setEffectiveFrom(r.effectiveFrom());
        a.setEffectiveTo(r.effectiveTo());
        if (r.costCentreId() != null) {
            CostCentre cc =
                    costCentres
                            .findByIdAndTenantId(r.costCentreId(), r.tenantId())
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Cost centre not found"));
            a.setCostCentre(cc);
        }
        if (r.businessUnitId() != null) {
            BusinessUnit bu =
                    businessUnits
                            .findByIdAndTenantId(r.businessUnitId(), r.tenantId())
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Business unit not found"));
            a.setBusinessUnit(bu);
        }
        if (r.departmentOrgUnitId() != null) {
            OrganizationUnit ou =
                    orgUnits.findByIdAndTenantId(r.departmentOrgUnitId(), r.tenantId())
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Department org unit not found"));
            a.setDepartmentOrgUnit(ou);
        }
        return toResponse(repository.save(a));
    }

    @Transactional(readOnly = true)
    public List<EmployeeCostAllocationResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return repository.findActiveForEmployee(tenantId, employeeId).stream()
                .map(EmployeeCostAllocationService::toResponse)
                .toList();
    }

    public void deactivate(UUID tenantId, UUID id) {
        EmployeeCostAllocation a =
                repository
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Cost allocation not found"));
        a.setActive(false);
    }

    static EmployeeCostAllocationResponse toResponse(EmployeeCostAllocation a) {
        return new EmployeeCostAllocationResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getEmployee() != null ? a.getEmployee().getId() : null,
                a.getCostCentre() != null ? a.getCostCentre().getId() : null,
                a.getCostCentre() != null ? a.getCostCentre().getCode() : null,
                a.getBusinessUnit() != null ? a.getBusinessUnit().getId() : null,
                a.getBusinessUnit() != null ? a.getBusinessUnit().getCode() : null,
                a.getDepartmentOrgUnit() != null ? a.getDepartmentOrgUnit().getId() : null,
                a.getDepartmentOrgUnit() != null ? a.getDepartmentOrgUnit().getCode() : null,
                a.getPercentage(),
                a.getEffectiveFrom(),
                a.getEffectiveTo(),
                a.isActive(),
                a.getVersionNo());
    }
}
