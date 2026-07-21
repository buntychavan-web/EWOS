package com.ewos.payroll.domain;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.infrastructure.persistence.EmployeeBankAccountRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeCompensationRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pre-flight validator for a payroll run. Given the list of employees that would be processed,
 * checks that each one has (a) an active compensation, (b) a primary bank account, and (c) an
 * active payroll profile. Missing profile is a warning; missing compensation or bank account is a
 * blocker.
 */
@Component
public final class PayrollValidator {

    private final EmployeeCompensationRepository compensations;
    private final EmployeeBankAccountRepository bankAccounts;
    private final EmployeePayrollProfileRepository profiles;

    public PayrollValidator(
            EmployeeCompensationRepository compensations,
            EmployeeBankAccountRepository bankAccounts,
            EmployeePayrollProfileRepository profiles) {
        this.compensations = compensations;
        this.bankAccounts = bankAccounts;
        this.profiles = profiles;
    }

    public PayrollValidationReport validate(UUID tenantId, List<Employee> employees) {
        List<PayrollValidationReport.Issue> blockers = new ArrayList<>();
        List<PayrollValidationReport.Issue> warnings = new ArrayList<>();

        for (Employee e : employees) {
            String name = displayName(e);
            if (compensations.findActiveForEmployee(tenantId, e.getId()).isEmpty()) {
                blockers.add(
                        new PayrollValidationReport.Issue(
                                e.getId(),
                                name,
                                "NO_ACTIVE_COMPENSATION",
                                "Employee has no active compensation record"));
            }
            if (bankAccounts.findPrimaryForEmployee(tenantId, e.getId()).isEmpty()) {
                blockers.add(
                        new PayrollValidationReport.Issue(
                                e.getId(),
                                name,
                                "NO_PRIMARY_BANK_ACCOUNT",
                                "Employee has no primary bank account for salary credit"));
            }
            if (profiles.findActiveForEmployee(tenantId, e.getId()).isEmpty()) {
                warnings.add(
                        new PayrollValidationReport.Issue(
                                e.getId(),
                                name,
                                "NO_PAYROLL_PROFILE",
                                "Employee has no active payroll profile; statutory identifiers"
                                        + " unknown"));
            }
        }
        return new PayrollValidationReport(blockers, warnings);
    }

    private static String displayName(Employee e) {
        if (e.getDisplayName() != null && !e.getDisplayName().isBlank()) {
            return e.getDisplayName();
        }
        StringBuilder sb = new StringBuilder();
        if (e.getFirstName() != null) {
            sb.append(e.getFirstName());
        }
        if (e.getLastName() != null) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(e.getLastName());
        }
        return sb.toString();
    }
}
