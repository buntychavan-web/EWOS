package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.api.dto.EmployeeTaxDeclarationResponse;
import com.ewos.payroll.api.dto.UpdateEmployeeTaxDeclarationRequest;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.infrastructure.persistence.EmployeeTaxDeclarationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin/HR CRUD for one employee's per-fiscal-year tax declaration — the inputs {@link
 * IncomeTaxCalculationService} needs beyond the payslip itself. The YTD accumulators on the same
 * row are read-only here; only the payroll run pipeline advances them.
 */
@Service
@Transactional
public class EmployeeTaxDeclarationService {

    private final EmployeeTaxDeclarationRepository repository;
    private final EmployeeRepository employees;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public EmployeeTaxDeclarationService(
            EmployeeTaxDeclarationRepository repository,
            EmployeeRepository employees,
            PayrollMapper mapper,
            ClientAccessGuard guard) {
        this.repository = repository;
        this.employees = employees;
        this.mapper = mapper;
        this.guard = guard;
    }

    public EmployeeTaxDeclarationResponse create(CreateEmployeeTaxDeclarationRequest request) {
        guard.requireAccessForCompany(request.companyId());
        Employee employee =
                employees
                        .findByIdAndTenantId(request.employeeId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (repository
                .findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                        request.tenantId(), request.employeeId(), request.fiscalYear())
                .isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "A tax declaration already exists for this employee and fiscal year");
        }
        EmployeeTaxDeclaration d = new EmployeeTaxDeclaration();
        d.setTenantId(request.tenantId());
        d.setCompanyId(request.companyId());
        d.setEmployee(employee);
        d.setFiscalYear(request.fiscalYear());
        d.setRegime(request.regime());
        if (request.previousEmployerIncome() != null) {
            d.setPreviousEmployerIncome(request.previousEmployerIncome());
        }
        if (request.otherIncome() != null) {
            d.setOtherIncome(request.otherIncome());
        }
        if (request.housePropertyLoss() != null) {
            d.setHousePropertyLoss(request.housePropertyLoss());
        }
        if (request.chapterViaDeclaredAmount() != null) {
            d.setChapterViaDeclaredAmount(request.chapterViaDeclaredAmount());
        }
        if (request.rentPaidAnnual() != null) {
            d.setRentPaidAnnual(request.rentPaidAnnual());
        }
        if (request.metroCity() != null) {
            d.setMetroCity(request.metroCity());
        }
        if (request.ltaExemptionDeclared() != null) {
            d.setLtaExemptionDeclared(request.ltaExemptionDeclared());
        }
        return mapper.toResponse(repository.save(d));
    }

    public EmployeeTaxDeclarationResponse update(
            UUID tenantId, UUID id, UpdateEmployeeTaxDeclarationRequest r) {
        EmployeeTaxDeclaration d = require(tenantId, id);
        guard.requireAccessForCompany(d.getCompanyId());
        if (r.regime() != null) {
            d.setRegime(r.regime());
        }
        if (r.previousEmployerIncome() != null) {
            d.setPreviousEmployerIncome(r.previousEmployerIncome());
        }
        if (r.otherIncome() != null) {
            d.setOtherIncome(r.otherIncome());
        }
        if (r.housePropertyLoss() != null) {
            d.setHousePropertyLoss(r.housePropertyLoss());
        }
        if (r.chapterViaDeclaredAmount() != null) {
            d.setChapterViaDeclaredAmount(r.chapterViaDeclaredAmount());
        }
        if (r.rentPaidAnnual() != null) {
            d.setRentPaidAnnual(r.rentPaidAnnual());
        }
        if (r.metroCity() != null) {
            d.setMetroCity(r.metroCity());
        }
        if (r.ltaExemptionDeclared() != null) {
            d.setLtaExemptionDeclared(r.ltaExemptionDeclared());
        }
        return mapper.toResponse(d);
    }

    @Transactional(readOnly = true)
    public EmployeeTaxDeclarationResponse getById(UUID tenantId, UUID id) {
        EmployeeTaxDeclaration d = require(tenantId, id);
        guard.requireAccessForCompany(d.getCompanyId());
        return mapper.toResponse(d);
    }

    @Transactional(readOnly = true)
    public EmployeeTaxDeclarationResponse forEmployeeAndYear(
            UUID tenantId, UUID employeeId, String fiscalYear) {
        EmployeeTaxDeclaration d =
                repository
                        .findByTenantIdAndEmployeeIdAndFiscalYearAndActiveTrue(
                                tenantId, employeeId, fiscalYear)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Tax declaration not found"));
        guard.requireAccessForCompany(d.getCompanyId());
        return mapper.toResponse(d);
    }

    @Transactional(readOnly = true)
    public List<EmployeeTaxDeclarationResponse> historyForEmployee(UUID tenantId, UUID employeeId) {
        List<EmployeeTaxDeclaration> found =
                repository.findAllByTenantIdAndEmployeeIdOrderByFiscalYearDesc(
                        tenantId, employeeId);
        guard.requireAccessForCompanies(
                found.stream().map(EmployeeTaxDeclaration::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    public void delete(UUID tenantId, UUID id) {
        EmployeeTaxDeclaration d = require(tenantId, id);
        guard.requireAccessForCompany(d.getCompanyId());
        repository.delete(d);
    }

    private EmployeeTaxDeclaration require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Tax declaration not found"));
    }
}
