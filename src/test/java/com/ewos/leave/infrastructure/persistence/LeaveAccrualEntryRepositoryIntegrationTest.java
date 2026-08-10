package com.ewos.leave.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.domain.LeaveAccrualEntry;
import com.ewos.leave.domain.LeaveAccrualEntry.EntryType;
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
 * Sprint 27D, extended by the Sprint 27D reconciliation — {@code leave_accrual_entries} against the
 * real database: {@link LeaveAccrualEntryRepository#existsForPeriod}/{@link
 * LeaveAccrualEntryRepository#existsCarryForwardForYear} are the idempotency checks {@link
 * com.ewos.leave.application.LeaveAccrualService}/{@code LeaveCarryForwardService} run before
 * crediting a period or a year, and {@code ux_leave_accrual_employee_type_period} / {@code
 * ux_leave_accrual_carry_forward_employee_type_year} (V78/V79) are the DB-level backstops for the
 * same invariants — this proves all of it actually works against Postgres, not just against a mock.
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

    /**
     * Sprint 27D reconciliation — carry-forward entries always land at accrual_month = 1 of the
     * destination year (see {@code LeaveCarryForwardService}), never used as a real MONTHLY_ACCRUAL
     * month for that same year in these tests, so the two coexist without collision.
     */
    private LeaveAccrualEntry carryForwardEntry(
            UUID tenantId, Employee employee, LeaveType type, LeaveBalance balance, int toYear) {
        LeaveAccrualEntry e = new LeaveAccrualEntry();
        e.setTenantId(tenantId);
        e.setCompanyId(COMPANY_ID);
        e.setEmployee(employee);
        e.setLeaveType(type);
        e.setLeaveBalance(balance);
        e.setAccrualYear(toYear);
        e.setAccrualMonth(1);
        e.setRequestedDays(new BigDecimal("3.00"));
        e.setCreditedDays(new BigDecimal("3.00"));
        e.setEntryType(EntryType.CARRY_FORWARD);
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

    /**
     * Sprint 27D reconciliation (F5-style tenant isolation, matching {@code
     * EmployeeRepositoryIntegrationTest}'s pattern) — {@code existsForPeriod} must never report an
     * entry as existing when it actually belongs to a different tenant, even for the exact same
     * (employee-shaped) period. Since {@code employee_id} is itself the true scoping key (an
     * employee only ever belongs to one tenant), this proves the two tenants' employees really are
     * distinct rows, not a shared identity accidentally matching across tenants.
     */
    @Test
    void existsForPeriodNeverCrossesTenantBoundaries() {
        UUID tenantA = tenant("AccrualTenantE1").getId();
        UUID tenantB = tenant("AccrualTenantE2").getId();
        Employee employeeA = employee(tenantA, "AccrualEmployeeE1");
        Employee employeeB = employee(tenantB, "AccrualEmployeeE2");
        LeaveType typeA = leaveType(tenantA);
        LeaveBalance balanceA = balance(tenantA, employeeA, typeA, 2026);
        entry(tenantA, employeeA, typeA, balanceA, 2026, 8);

        assertThat(entries.existsForPeriod(employeeA.getId(), typeA.getId(), 2026, 8)).isTrue();
        assertThat(entries.existsForPeriod(employeeB.getId(), typeA.getId(), 2026, 8)).isFalse();
    }

    @Test
    void existsCarryForwardForYearReturnsFalseWhenNoCarryForwardEntryHasBeenWritten() {
        UUID tenantA = tenant("CarryForwardTenantA").getId();
        Employee employee = employee(tenantA, "CarryForwardEmployeeA");
        LeaveType type = leaveType(tenantA);

        assertThat(entries.existsCarryForwardForYear(employee.getId(), type.getId(), 2027))
                .isFalse();
    }

    @Test
    void existsCarryForwardForYearReturnsTrueOnlyForTheExactYearACarryForwardEntryWasWrittenFor() {
        UUID tenantA = tenant("CarryForwardTenantB").getId();
        Employee employee = employee(tenantA, "CarryForwardEmployeeB");
        LeaveType type = leaveType(tenantA);
        LeaveBalance balance2027 = balance(tenantA, employee, type, 2027);
        carryForwardEntry(tenantA, employee, type, balance2027, 2027);

        assertThat(entries.existsCarryForwardForYear(employee.getId(), type.getId(), 2027))
                .isTrue();
        assertThat(entries.existsCarryForwardForYear(employee.getId(), type.getId(), 2028))
                .isFalse();
    }

    /**
     * The application-level {@code existsCarryForwardForYear} check is the fast path {@code
     * LeaveCarryForwardService} relies on; this proves {@code
     * ux_leave_accrual_carry_forward_employee_type_year} (V79) is a real, enforced DB backstop
     * underneath it, mirroring {@link
     * #theUniqueIndexRejectsASecondEntryForTheSameEmployeeLeaveTypeAndPeriod} for the carry-forward
     * index.
     */
    @Test
    void theCarryForwardUniqueIndexRejectsASecondEntryForTheSameEmployeeLeaveTypeAndYear() {
        UUID tenantA = tenant("CarryForwardTenantC").getId();
        Employee employee = employee(tenantA, "CarryForwardEmployeeC");
        LeaveType type = leaveType(tenantA);
        LeaveBalance balance2027 = balance(tenantA, employee, type, 2027);
        carryForwardEntry(tenantA, employee, type, balance2027, 2027);

        assertThatThrownBy(() -> carryForwardEntry(tenantA, employee, type, balance2027, 2027))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * Sprint 27D reconciliation — proves the V79 migration's narrowing of {@code
     * ux_leave_accrual_employee_type_period} to {@code entry_type = 'MONTHLY_ACCRUAL'} actually
     * works: a real January MONTHLY_ACCRUAL row and a CARRY_FORWARD row (which always lands at
     * accrual_month = 1) for the very same employee/leaveType/year must both be able to exist side
     * by side, since they are legitimately different transactions.
     */
    @Test
    void aJanuaryMonthlyAccrualEntryAndACarryForwardEntryForTheSameYearDoNotCollide() {
        UUID tenantA = tenant("CarryForwardTenantD").getId();
        Employee employee = employee(tenantA, "CarryForwardEmployeeD");
        LeaveType type = leaveType(tenantA);
        LeaveBalance balance2027 = balance(tenantA, employee, type, 2027);

        entry(tenantA, employee, type, balance2027, 2027, 1);
        LeaveAccrualEntry carryForward =
                carryForwardEntry(tenantA, employee, type, balance2027, 2027);

        assertThat(carryForward.getId()).isNotNull();
        assertThat(entries.existsForPeriod(employee.getId(), type.getId(), 2027, 1)).isTrue();
        assertThat(entries.existsCarryForwardForYear(employee.getId(), type.getId(), 2027))
                .isTrue();
    }
}
