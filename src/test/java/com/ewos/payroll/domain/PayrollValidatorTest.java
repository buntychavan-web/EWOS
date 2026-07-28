package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.infrastructure.persistence.EmployeeBankAccountRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeeCompensationRepository;
import com.ewos.payroll.infrastructure.persistence.EmployeePayrollProfileRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pre-flight payroll validation: missing compensation/bank account block a run, a missing payroll
 * profile only warns.
 */
@ExtendWith(MockitoExtension.class)
class PayrollValidatorTest {

    @Mock EmployeeCompensationRepository compensations;
    @Mock EmployeeBankAccountRepository bankAccounts;
    @Mock EmployeePayrollProfileRepository profiles;

    private PayrollValidator validator;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validator = new PayrollValidator(compensations, bankAccounts, profiles);
    }

    private Employee employee(String first, String last) {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        e.setFirstName(first);
        e.setLastName(last);
        return e;
    }

    @Test
    void validateIsRunnableWhenEveryEmployeeHasCompensationAndABankAccount() {
        Employee e = employee("Asha", "Rao");
        when(compensations.findActiveForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeCompensation()));
        when(bankAccounts.findPrimaryForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeBankAccount()));
        when(profiles.findActiveForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeePayrollProfile()));

        PayrollValidationReport report = validator.validate(tenantId, List.of(e));

        assertThat(report.isRunnable()).isTrue();
        assertThat(report.blockers()).isEmpty();
        assertThat(report.warnings()).isEmpty();
    }

    @Test
    void validateBlocksWhenAnEmployeeHasNoActiveCompensation() {
        Employee e = employee("Asha", "Rao");
        when(compensations.findActiveForEmployee(tenantId, e.getId())).thenReturn(Optional.empty());
        when(bankAccounts.findPrimaryForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeBankAccount()));
        when(profiles.findActiveForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeePayrollProfile()));

        PayrollValidationReport report = validator.validate(tenantId, List.of(e));

        assertThat(report.isRunnable()).isFalse();
        assertThat(report.blockers()).hasSize(1);
        assertThat(report.blockers().get(0).code()).isEqualTo("NO_ACTIVE_COMPENSATION");
        assertThat(report.blockers().get(0).employeeName()).isEqualTo("Asha Rao");
    }

    @Test
    void validateBlocksWhenAnEmployeeHasNoPrimaryBankAccount() {
        Employee e = employee("Asha", "Rao");
        when(compensations.findActiveForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeCompensation()));
        when(bankAccounts.findPrimaryForEmployee(tenantId, e.getId())).thenReturn(Optional.empty());
        when(profiles.findActiveForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeePayrollProfile()));

        PayrollValidationReport report = validator.validate(tenantId, List.of(e));

        assertThat(report.isRunnable()).isFalse();
        assertThat(report.blockers().get(0).code()).isEqualTo("NO_PRIMARY_BANK_ACCOUNT");
    }

    @Test
    void validateOnlyWarnsWhenAnEmployeeHasNoPayrollProfile() {
        Employee e = employee("Asha", "Rao");
        when(compensations.findActiveForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeCompensation()));
        when(bankAccounts.findPrimaryForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeBankAccount()));
        when(profiles.findActiveForEmployee(tenantId, e.getId())).thenReturn(Optional.empty());

        PayrollValidationReport report = validator.validate(tenantId, List.of(e));

        // A missing profile is advisory only — the run must still be considered runnable.
        assertThat(report.isRunnable()).isTrue();
        assertThat(report.blockers()).isEmpty();
        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().get(0).code()).isEqualTo("NO_PAYROLL_PROFILE");
    }

    @Test
    void validateUsesDisplayNameOverFirstLastWhenPresent() {
        Employee e = employee("Asha", "Rao");
        e.setDisplayName("A. Rao (Preferred)");
        when(compensations.findActiveForEmployee(tenantId, e.getId())).thenReturn(Optional.empty());
        when(bankAccounts.findPrimaryForEmployee(tenantId, e.getId())).thenReturn(Optional.empty());
        when(profiles.findActiveForEmployee(tenantId, e.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeePayrollProfile()));

        PayrollValidationReport report = validator.validate(tenantId, List.of(e));

        assertThat(report.blockers()).allMatch(i -> "A. Rao (Preferred)".equals(i.employeeName()));
    }

    @Test
    void validateAggregatesIssuesAcrossMultipleEmployeesIndependently() {
        Employee good = employee("Good", "Employee");
        Employee bad = employee("Bad", "Employee");
        when(compensations.findActiveForEmployee(tenantId, good.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeCompensation()));
        when(bankAccounts.findPrimaryForEmployee(tenantId, good.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeeBankAccount()));
        when(profiles.findActiveForEmployee(tenantId, good.getId()))
                .thenReturn(Optional.of(new com.ewos.payroll.domain.EmployeePayrollProfile()));
        when(compensations.findActiveForEmployee(tenantId, bad.getId()))
                .thenReturn(Optional.empty());
        when(bankAccounts.findPrimaryForEmployee(tenantId, bad.getId()))
                .thenReturn(Optional.empty());
        when(profiles.findActiveForEmployee(tenantId, bad.getId())).thenReturn(Optional.empty());

        PayrollValidationReport report = validator.validate(tenantId, List.of(good, bad));

        assertThat(report.blockers()).hasSize(2);
        assertThat(report.blockers()).allMatch(i -> i.employeeId().equals(bad.getId()));
        assertThat(report.warnings()).hasSize(1);
        assertThat(report.warnings().get(0).employeeId()).isEqualTo(bad.getId());
    }
}
