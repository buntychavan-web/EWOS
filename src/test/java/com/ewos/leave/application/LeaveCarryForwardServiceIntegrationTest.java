package com.ewos.leave.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardOutcome;
import com.ewos.leave.application.LeaveCarryForwardService.CarryForwardResult;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveAccrualEntryRepository;
import com.ewos.leave.infrastructure.persistence.LeaveBalanceRepository;
import com.ewos.leave.infrastructure.persistence.LeaveTypeRepository;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sprint 27D reconciliation — {@link LeaveCarryForwardService} against the real database: the
 * credited amount and ledger entry actually persist, a manual {@code adjustmentDays} value is left
 * untouched (decision 12), a sequential re-run never double-credits (decision 7/13's app-level
 * check), and two concurrent attempts at the very same (employee, leaveType, toYear) are still only
 * ever credited once — the DB unique index (V79) is the backstop when two {@code REQUIRES_NEW}
 * transactions both pass the app-level idempotency check before either commits, exactly as {@link
 * LeaveCarryForwardJob}'s docstring already discloses this architecture assumes (a single active
 * scheduler instance; concurrent runs are a race the DB constraint — not application logic — is
 * responsible for closing safely).
 */
class LeaveCarryForwardServiceIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired LeaveCarryForwardService carryForwardService;
    @Autowired LeaveBalanceRepository balances;
    @Autowired LeaveTypeRepository leaveTypes;
    @Autowired LeaveAccrualEntryRepository entries;
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
        e.setLastName("CarryForward");
        e.setWorkEmail(label.toLowerCase(Locale.ROOT) + SEQ.get() + "@example.com");
        e.setHireDate(LocalDate.of(2020, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        return employees.save(e);
    }

    private LeaveType leaveType(UUID tenantId, BigDecimal carryForwardDays) {
        LeaveType t = new LeaveType();
        t.setTenantId(tenantId);
        t.setCode("CF_IT_" + SEQ.incrementAndGet());
        t.setName("Carry Forward Integration Test Leave Type");
        t.setPaid(true);
        t.setCarryForwardDays(carryForwardDays);
        return leaveTypes.save(t);
    }

    private LeaveBalance balanceWithAccrued(
            UUID tenantId, Employee employee, LeaveType type, int year, BigDecimal accruedDays) {
        LeaveBalance b = new LeaveBalance();
        b.setTenantId(tenantId);
        b.setCompanyId(COMPANY_ID);
        b.setEmployee(employee);
        b.setLeaveType(type);
        b.setYear(year);
        b.setAccruedDays(accruedDays);
        return balances.save(b);
    }

    @Test
    void creditedCarryForwardPersistsOnTheDestinationYearsBalanceAndWritesALedgerEntry() {
        UUID tenantId = tenant("CfPersistTenant").getId();
        Employee employee = employee(tenantId, "CfPersistEmployee");
        LeaveType type = leaveType(tenantId, new BigDecimal("5.00"));
        balanceWithAccrued(tenantId, employee, type, 2026, new BigDecimal("6.00"));

        CarryForwardResult result =
                carryForwardService.carryForwardForEmployee(employee, type, 2026);

        assertThat(result.outcome()).isEqualTo(CarryForwardOutcome.CREDITED);
        assertThat(result.creditedDays()).isEqualByComparingTo("5.00");

        LeaveBalance persisted =
                balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), 2027)
                        .orElseThrow();
        assertThat(persisted.getCarryForwardDays()).isEqualByComparingTo("5.00");
        assertThat(persisted.getAdjustmentDays()).isEqualByComparingTo("0.00");

        assertThat(entries.existsCarryForwardForYear(employee.getId(), type.getId(), 2027))
                .isTrue();
    }

    @Test
    void doesNotOverwriteAPreExistingManualAdjustmentOnTheDestinationBalance() {
        UUID tenantId = tenant("CfAdjustmentTenant").getId();
        Employee employee = employee(tenantId, "CfAdjustmentEmployee");
        LeaveType type = leaveType(tenantId, new BigDecimal("5.00"));
        balanceWithAccrued(tenantId, employee, type, 2026, new BigDecimal("6.00"));
        // A manual adjustment already applied to the destination year, before carry-forward runs.
        LeaveBalance destination2027 =
                balanceWithAccrued(tenantId, employee, type, 2027, BigDecimal.ZERO);
        destination2027.setAdjustmentDays(new BigDecimal("2.00"));
        balances.save(destination2027);

        carryForwardService.carryForwardForEmployee(employee, type, 2026);

        LeaveBalance persisted =
                balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), 2027)
                        .orElseThrow();
        assertThat(persisted.getAdjustmentDays()).isEqualByComparingTo("2.00");
        assertThat(persisted.getCarryForwardDays()).isEqualByComparingTo("5.00");
    }

    @Test
    void aSequentialRerunNeverDoubleCreditsTheDestinationBalance() {
        UUID tenantId = tenant("CfRerunTenant").getId();
        Employee employee = employee(tenantId, "CfRerunEmployee");
        LeaveType type = leaveType(tenantId, new BigDecimal("5.00"));
        balanceWithAccrued(tenantId, employee, type, 2026, new BigDecimal("6.00"));

        CarryForwardResult first =
                carryForwardService.carryForwardForEmployee(employee, type, 2026);
        CarryForwardResult second =
                carryForwardService.carryForwardForEmployee(employee, type, 2026);

        assertThat(first.outcome()).isEqualTo(CarryForwardOutcome.CREDITED);
        assertThat(second.outcome()).isEqualTo(CarryForwardOutcome.ALREADY_PROCESSED);

        LeaveBalance persisted =
                balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), 2027)
                        .orElseThrow();
        assertThat(persisted.getCarryForwardDays()).isEqualByComparingTo("5.00");
    }

    /**
     * Two concurrent attempts to carry forward the very same (employee, leaveType, toYear): both
     * threads' {@code REQUIRES_NEW} transactions can observe "not yet processed" before either
     * commits, so the app-level check alone cannot prevent both from trying to insert a ledger row
     * — {@code ux_leave_accrual_carry_forward_employee_type_year} (V79) is what actually stops the
     * double-credit, exactly as {@link LeaveCarryForwardJob}'s docstring discloses. Exactly one
     * attempt must end up credited; the destination balance must reflect that credit exactly once,
     * never twice.
     */
    @Test
    void concurrentCarryForwardAttemptsCreditTheDestinationBalanceOnlyOnce() throws Exception {
        UUID tenantId = tenant("CfConcurrentTenant").getId();
        Employee employee = employee(tenantId, "CfConcurrentEmployee");
        LeaveType type = leaveType(tenantId, new BigDecimal("5.00"));
        balanceWithAccrued(tenantId, employee, type, 2026, new BigDecimal("6.00"));

        int attempts = 2;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        List<CarryForwardResult> succeeded = new CopyOnWriteArrayList<>();
        List<Exception> failed = new CopyOnWriteArrayList<>();
        try {
            for (int i = 0; i < attempts; i++) {
                pool.submit(
                        () -> {
                            try {
                                ready.countDown();
                                go.await();
                                succeeded.add(
                                        carryForwardService.carryForwardForEmployee(
                                                employee, type, 2026));
                            } catch (Exception e) {
                                failed.add(e);
                            }
                        });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // Either the DB constraint surfaced as an exception on the losing thread, or (less likely,
        // but possible depending on exact timing) the losing thread's own existsCarryForwardForYear
        // check ran after the winner had already committed and it legitimately observed
        // ALREADY_PROCESSED — both outcomes are correct; what must never happen is two CREDITED
        // results.
        long creditedCount =
                succeeded.stream().filter(r -> r.outcome() == CarryForwardOutcome.CREDITED).count();
        assertThat(creditedCount).isEqualTo(1);
        assertThat(succeeded.size() + failed.size()).isEqualTo(attempts);

        LeaveBalance persisted =
                balances.findByEmployeeTypeYear(tenantId, employee.getId(), type.getId(), 2027)
                        .orElseThrow();
        assertThat(persisted.getCarryForwardDays()).isEqualByComparingTo("5.00");

        assertThat(entries.existsCarryForwardForYear(employee.getId(), type.getId(), 2027))
                .isTrue();
    }
}
