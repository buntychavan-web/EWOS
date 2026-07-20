package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEmployeePayrollProfileRequest;
import com.ewos.payroll.api.dto.EmployeePayrollProfileResponse;
import com.ewos.payroll.domain.EmployeePayrollProfile;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages per-employee payroll profiles. A new profile supersedes any previously active one — the
 * previous row is deactivated and its {@code effectiveTo} set to the new record's start date minus
 * one day (if not already set).
 */
@Service
@Transactional
public class EmployeePayrollProfileService {

    private final EmployeePayrollProfileRepository repository;
    private final EmployeeRepository employees;
    private final PayGroupService payGroups;
    private final PayrollMapper mapper;

    public EmployeePayrollProfileService(
            EmployeePayrollProfileRepository repository,
            EmployeeRepository employees,
            PayGroupService payGroups,
            PayrollMapper mapper) {
        this.repository = repository;
        this.employees = employees;
        this.payGroups = payGroups;
        this.mapper = mapper;
    }

    public EmployeePayrollProfileResponse create(CreateEmployeePayrollProfileRequest request) {
        Employee employee =
                employees
                        .findByIdAndTenantId(request.employeeId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }
        PayGroup group = null;
        if (request.payGroupId() != null) {
            group = payGroups.require(request.tenantId(), request.payGroupId());
            if (!group.getCompanyId().equals(request.companyId())) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "Pay group belongs to a different company");
            }
        }
        repository
                .findActiveForEmployee(request.tenantId(), request.employeeId())
                .ifPresent(
                        existing -> {
                            existing.setActive(false);
                            if (existing.getEffectiveTo() == null) {
                                existing.setEffectiveTo(request.effectiveFrom().minusDays(1));
                            }
                        });

        EmployeePayrollProfile p = new EmployeePayrollProfile();
        p.setTenantId(request.tenantId());
        p.setCompanyId(request.companyId());
        p.setEmployee(employee);
        p.setPayGroup(group);
        p.setTaxRegime(request.taxRegime());
        p.setCountryCode(request.countryCode());
        p.setStatutoryIdentifiersJson(
                PayrollMapper.writeIdentifiers(request.statutoryIdentifiers()));
        p.setEffectiveFrom(request.effectiveFrom());
        p.setEffectiveTo(request.effectiveTo());
        p.setActive(true);
        return mapper.toResponse(repository.save(p));
    }

    @Transactional(readOnly = true)
    public EmployeePayrollProfileResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public EmployeePayrollProfileResponse activeForEmployee(UUID tenantId, UUID employeeId) {
        EmployeePayrollProfile p =
                repository
                        .findActiveForEmployee(tenantId, employeeId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No active payroll profile for employee "
                                                        + employeeId));
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<EmployeePayrollProfileResponse> historyForEmployee(UUID tenantId, UUID employeeId) {
        return repository.findHistoryForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmployeePayrollProfileResponse> forPayGroup(UUID tenantId, UUID payGroupId) {
        return repository.findActiveByPayGroup(tenantId, payGroupId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public EmployeePayrollProfile require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "Employee payroll profile not found"));
    }
}
