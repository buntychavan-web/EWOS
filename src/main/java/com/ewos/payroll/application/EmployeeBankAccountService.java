package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreateEmployeeBankAccountRequest;
import com.ewos.payroll.api.dto.EmployeeBankAccountResponse;
import com.ewos.payroll.domain.EmployeeBankAccount;
import com.ewos.payroll.infrastructure.persistence.EmployeeBankAccountRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employee bank accounts. When a new account is created with {@code primary=true} any existing
 * primary account for that employee is demoted so the partial unique index on primary+active is
 * respected.
 */
@Service
@Transactional
public class EmployeeBankAccountService {

    private final EmployeeBankAccountRepository repository;
    private final EmployeeRepository employees;
    private final PayrollMapper mapper;

    public EmployeeBankAccountService(
            EmployeeBankAccountRepository repository,
            EmployeeRepository employees,
            PayrollMapper mapper) {
        this.repository = repository;
        this.employees = employees;
        this.mapper = mapper;
    }

    public EmployeeBankAccountResponse create(CreateEmployeeBankAccountRequest request) {
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
        boolean primary = request.primary() == null || request.primary();
        if (primary) {
            repository
                    .findPrimaryForEmployee(request.tenantId(), request.employeeId())
                    .ifPresent(existing -> existing.setPrimary(false));
        }
        EmployeeBankAccount b = new EmployeeBankAccount();
        b.setTenantId(request.tenantId());
        b.setCompanyId(request.companyId());
        b.setEmployee(employee);
        b.setBankName(request.bankName());
        b.setBranch(request.branch());
        b.setAccountHolderName(request.accountHolderName());
        b.setAccountNumber(request.accountNumber());
        b.setAccountNumberMasked(mask(request.accountNumber()));
        b.setRoutingCode(request.routingCode());
        b.setSwiftBic(request.swiftBic());
        b.setCurrency(request.currency());
        b.setCountryCode(request.countryCode());
        b.setPrimary(primary);
        return mapper.toResponse(repository.save(b));
    }

    public EmployeeBankAccountResponse setPrimary(UUID tenantId, UUID id) {
        EmployeeBankAccount b = require(tenantId, id);
        if (!b.isActive()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Inactive accounts cannot be marked primary");
        }
        UUID employeeId = b.getEmployee().getId();
        repository
                .findPrimaryForEmployee(tenantId, employeeId)
                .ifPresent(existing -> existing.setPrimary(false));
        b.setPrimary(true);
        return mapper.toResponse(b);
    }

    public void deactivate(UUID tenantId, UUID id) {
        EmployeeBankAccount b = require(tenantId, id);
        b.setActive(false);
        b.setPrimary(false);
    }

    @Transactional(readOnly = true)
    public EmployeeBankAccountResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<EmployeeBankAccountResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return repository.findAllForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeBankAccount requirePrimary(UUID tenantId, UUID employeeId) {
        return repository
                .findPrimaryForEmployee(tenantId, employeeId)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.UNPROCESSABLE_ENTITY,
                                        "No primary bank account for employee " + employeeId));
    }

    public EmployeeBankAccount require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Bank account not found"));
    }

    static String mask(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String trimmed = accountNumber.trim();
        if (trimmed.length() <= 4) {
            return "*".repeat(Math.max(0, trimmed.length()));
        }
        int visible = 4;
        int maskedLen = trimmed.length() - visible;
        return "*".repeat(maskedLen) + trimmed.substring(maskedLen);
    }
}
