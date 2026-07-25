package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.ArrearResponse;
import com.ewos.payroll.api.dto.CreateArrearRequest;
import com.ewos.payroll.domain.PayrollArrear;
import com.ewos.payroll.infrastructure.persistence.PayrollArrearRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues retro salary adjustments (arrears) for an employee. Pending arrears are picked up
 * automatically by the next payroll run for that employee's company; once applied the row is frozen
 * and cannot be edited.
 */
@Service
@Transactional
public class PayrollArrearService {

    private final PayrollArrearRepository repository;
    private final EmployeeRepository employees;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public PayrollArrearService(
            PayrollArrearRepository repository,
            EmployeeRepository employees,
            PayrollMapper mapper,
            ClientAccessGuard guard) {
        this.repository = repository;
        this.employees = employees;
        this.mapper = mapper;
        this.guard = guard;
    }

    public ArrearResponse create(CreateArrearRequest request) {
        guard.requireAccessForCompany(request.companyId());
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
        PayrollArrear a = new PayrollArrear();
        a.setTenantId(request.tenantId());
        a.setCompanyId(request.companyId());
        a.setEmployee(employee);
        a.setReasonCode(request.reasonCode());
        a.setDescription(request.description());
        a.setAmount(request.amount());
        a.setKind(request.kind());
        a.setForPeriodStart(request.forPeriodStart());
        a.setForPeriodEnd(request.forPeriodEnd());
        return mapper.toResponse(repository.save(a));
    }

    public void cancel(UUID tenantId, UUID id) {
        PayrollArrear a = require(tenantId, id);
        guard.requireAccessForCompany(a.getCompanyId());
        if (a.isApplied()) {
            throw new ApiException(HttpStatus.CONFLICT, "Applied arrears cannot be cancelled");
        }
        repository.delete(a);
    }

    @Transactional(readOnly = true)
    public ArrearResponse getById(UUID tenantId, UUID id) {
        PayrollArrear a = require(tenantId, id);
        guard.requireAccessForCompany(a.getCompanyId());
        return mapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public List<ArrearResponse> forEmployee(UUID tenantId, UUID employeeId) {
        List<PayrollArrear> found = repository.findAllForEmployee(tenantId, employeeId);
        guard.requireAccessForCompanies(found.stream().map(PayrollArrear::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ArrearResponse> pendingForEmployee(UUID tenantId, UUID employeeId) {
        List<PayrollArrear> found = repository.findPendingForEmployee(tenantId, employeeId);
        guard.requireAccessForCompanies(found.stream().map(PayrollArrear::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ArrearResponse> forRun(UUID tenantId, UUID runId) {
        List<PayrollArrear> found = repository.findForRun(tenantId, runId);
        guard.requireAccessForCompanies(found.stream().map(PayrollArrear::getCompanyId).toList());
        return found.stream().map(mapper::toResponse).toList();
    }

    public PayrollArrear require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Arrear not found"));
    }
}
