package com.ewos.leave.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.domain.LeaveAccrualEntry;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveType;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27D — {@code leave_accrual_entries} against the real database: {@link
 * LeaveAccrualEntryRepository#existsForPeriod} is the idempotency check {@link
 * com.ewos.leave.application.LeaveAccrualService} runs before crediting a period, and {@code
 * ux_leave_accrual_employee_type_period} (V78) is the DB-level backstop for the same invariant —
 * this proves both halves actually work against Postgres, not just against a mock.
 */
@Transactional
class LeaveAccrualEntryRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired LeaveAccrualEntryRepository entries;
    @Autowired LeaveBalanceRepository balances;
    @Autowired LeaveTypeRepository leaveTypes;
    @Autowired EmployeeRepository employees;
    @Autowired TenantRepository tenants;

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCode(label + "-" + SEQ.incrementAndGet());
        t.setName(label);
        return tenants.save(t);
    }

    private Employee employee(UUID tenantId, String label) {
        Employee e = new Employee();
        e.setTenantId(tenantId);
        e.setCompanyId(COMPANY_ID);
        e.setEmployeeNumber(label + "-" + SEQ.incrementAndGet());
        e.setFirstName(label);
        e.setLastName("Accruer");
        e.setWorkEmail(label.toLowerCase(Locale.ROOT) + SEQ.get() + "@example.com");
        e.setHireDate(LocalDate.of(2020, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        return employees.save(e);
    }

    private LeaveType leaveType(UUID tenantId) {
        LeaveType t = new LeaveType();
        t.setTenantId(tenantId);
        t.setCode("ACCRUAL_IT_" + SEQ.incrementAndGet());
        t.setName("Accrual Integration Test Leave Type");
        t.setPaid(true);
        t.setAccrualDaysPerYear(new BigDecimal("24.00"));
        return leaveTypes.save(t);
    }

    private LeaveBalance balance(UUID tenantId, Employee employee, LeaveType type, int year) {
        LeaveBalance b = new LeaveBalance();
        b.setTenantId(tenantId);
        b.setCompanyId(COMPANY_ID);
        b.setEmployee(employee);
        b.setLeaveType(type);
        b.setYear(year);
        return balances.save(b);
    }

    private LeaveAccrualEntry entry(
            UUID tenantId,
            Employee employee,
            LeaveType type,
            LeaveBalance balance,
            int year,
            int month) {
        LeaveAccrualEntry e = new LeaveAccrualEntry();
        e.setTenantId(tenantId);
        e.setCompanyId(COMPANY_ID);
        e.setEmployee(employee);
        e.setLeaveType(type);
        e.setLeaveBalance(balance);
        e.setAccrualYear(year);
        e.setAccrualMonth(month);
        e.setRequestedDays(new BigDecimal("2.00"));
        e.setCreditedDays(new BigDecimal("2.00"));
        // saveAndFlush (not save) so a unique-constraint violation surfaces synchronously here,
        // where assertThatThrownBy can catch it, rather than silently deferring to the next
        // flush point (Hibernate can assign this entity's @UuidGenerator id client-side without
        // an immediate DB round trip, so a plain save() would not reliably surface the violation
        // at the call site).
        return entries.saveAndFlush(e);
    }

    @Test
    void existsForPeriodReturnsFalseWhenNoEntryHasBeenWrittenYet() {
        UUID tenantA = tenant("AccrualTenantA").getId();
        Employee employee = employee(tenantA, "AccrualEmployeeA");
        LeaveType type = leaveType(tenantA);

        boolean exists = entries.existsForPeriod(employee.getId(), type.getId(), 2026, 8);

        assertThat(exists).isFalse();
    }

    @Test
    void existsForPeriodReturnsTrueOnlyForTheExactPeriodAnEntryWasWrittenFor() {
        UUID tenantA = tenant("AccrualTenantB").getId();
        Employee employee = employee(tenantA, "AccrualEmployeeB");
        LeaveType type = leaveType(tenantA);
        LeaveBalance balance = balance(tenantA, employee, type, 2026);
        entry(tenantA, employee, type, balance, 2026, 8);

        assertThat(entries.existsForPeriod(employee.getId(), type.getId(), 2026, 8)).isTrue();
        assertThat(entries.existsForPeriod(employee.getId(), type.getId(), 2026, 9)).isFalse();
        assertThat(entries.existsForPeriod(employee.getId(), type.getId(), 2025, 8)).isFalse();
    }

    @Test
    void existsForPeriodIsScopedPerEmployeeNotJustPerLeaveTypeAndPeriod() {
        UUID tenantA = tenant("AccrualTenantC").getId();
        Employee employeeWithEntry = employee(tenantA, "AccrualEmployeeC1");
        Employee employeeWithoutEntry = employee(tenantA, "AccrualEmployeeC2");
        LeaveType type = leaveType(tenantA);
        LeaveBalance balance = balance(tenantA, employeeWithEntry, type, 2026);
        entry(tenantA, employeeWithEntry, type, balance, 2026, 8);

        assertThat(entries.existsForPeriod(employeeWithEntry.getId(), type.getId(), 2026, 8))
                .isTrue();
        assertThat(entries.existsForPeriod(employeeWithoutEntry.getId(), type.getId(), 2026, 8))
                .isFalse();
    }

    /**
     * The application-level {@code existsForPeriod} check is the fast path {@code
     * LeaveAccrualService} relies on in normal operation; this proves the unique index is a real,
     * enforced backstop underneath it — not just a check that happens to never be exercised.
     */
    @Test
    void theUniqueIndexRejectsASecondEntryForTheSameEmployeeLeaveTypeAndPeriod() {
        UUID tenantA = tenant("AccrualTenantD").getId();
        Employee employee = employee(tenantA, "AccrualEmployeeD");
        LeaveType type = leaveType(tenantA);
        LeaveBalance balance = balance(tenantA, employee, type, 2026);
        entry(tenantA, employee, type, balance, 2026, 8);

        assertThatThrownBy(() -> entry(tenantA, employee, type, balance, 2026, 8))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
