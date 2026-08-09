package com.ewos.payroll.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.Payslip;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27C fix-round (F2/F5) — {@code findRecentForEmployee} and {@code
 * findAllForEmployeeInPeriod} against the real database, proving the ESS dashboard's
 * payroll-snapshot queries are genuinely bounded (never the full unbounded history) and
 * tenant-isolated, mirroring {@code EmployeeRepositoryIntegrationTest}'s defense-in-depth pattern:
 * {@code payslips.tenant_id} carries no foreign key (unlike {@code employees.tenant_id}), so a
 * corrupted/mismatched tenant id on a payslip row is constructible directly, and the query itself —
 * not just upstream service validation — must still never let it leak into another tenant's result.
 */
@Transactional
class PayslipRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired PayslipRepository payslips;
    @Autowired EmployeeRepository employees;
    @Autowired PayrollPeriodRepository periods;
    @Autowired PayrollRunRepository runs;
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
        e.setLastName("Payee");
        e.setWorkEmail(label.toLowerCase(Locale.ROOT) + SEQ.get() + "@example.com");
        e.setHireDate(LocalDate.of(2020, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        return employees.save(e);
    }

    private PayrollPeriod period(UUID tenantId, LocalDate periodStart) {
        PayrollPeriod p = new PayrollPeriod();
        p.setTenantId(tenantId);
        p.setCompanyId(COMPANY_ID);
        p.setCode("PS-IT-" + SEQ.incrementAndGet());
        p.setName("Payslip repository IT period");
        p.setFrequency(PayrollFrequency.MONTHLY);
        p.setPeriodStart(periodStart);
        p.setPeriodEnd(periodStart.plusDays(27));
        p.setPayDate(periodStart.plusDays(30));
        p.setStatus(PayrollPeriodStatus.LOCKED);
        p.setLockedAt(java.time.Instant.now());
        p.setLockedBy(tenantId);
        return periods.save(p);
    }

    private PayrollRun run(UUID tenantId, PayrollPeriod period) {
        PayrollRun r = new PayrollRun();
        r.setTenantId(tenantId);
        r.setCompanyId(COMPANY_ID);
        r.setPayrollPeriod(period);
        return runs.save(r);
    }

    private Payslip payslip(
            UUID tenantId,
            Employee employee,
            PayrollRun run,
            PayrollPeriod period,
            LocalDate periodStart) {
        Payslip p = new Payslip();
        p.setTenantId(tenantId);
        p.setCompanyId(COMPANY_ID);
        p.setPayrollRun(run);
        p.setPayrollPeriod(period);
        p.setEmployee(employee);
        p.setEmployeeNumberSnapshot(employee.getEmployeeNumber());
        p.setEmployeeNameSnapshot(employee.getFirstName() + " " + employee.getLastName());
        p.setPeriodStart(periodStart);
        p.setPeriodEnd(periodStart.plusDays(27));
        p.setPayDate(periodStart.plusDays(30));
        return payslips.save(p);
    }

    @Test
    void findRecentForEmployeeReturnsOnlyTheMostRecentRowWhenBoundedToOne() {
        UUID tenantA = tenant("PsRecentTenantA").getId();
        Employee employeeA = employee(tenantA, "PsRecentEmployee");
        PayrollPeriod olderPeriod = period(tenantA, LocalDate.of(2025, 1, 1));
        PayrollPeriod newerPeriod = period(tenantA, LocalDate.of(2026, 6, 1));
        PayrollRun olderRun = run(tenantA, olderPeriod);
        PayrollRun newerRun = run(tenantA, newerPeriod);
        payslip(tenantA, employeeA, olderRun, olderPeriod, LocalDate.of(2025, 1, 1));
        Payslip newest =
                payslip(tenantA, employeeA, newerRun, newerPeriod, LocalDate.of(2026, 6, 1));

        List<Payslip> result =
                payslips.findRecentForEmployee(tenantA, employeeA.getId(), PageRequest.of(0, 1));

        assertThat(result).extracting(Payslip::getId).containsExactly(newest.getId());
    }

    /**
     * Sprint 27C fix-round (F2) — the exact regression this query exists to prevent: the ESS
     * dashboard's payroll snapshot must never depend on loading an employee's entire payslip
     * history, only the single most recent row (or the current-year window, in {@code
     * findAllForEmployeeInPeriod} below). This asserts the bounded query itself never returns more
     * than the requested page size even when far more history exists.
     */
    @Test
    void findRecentForEmployeeNeverReturnsMoreThanTheRequestedPageSizeRegardlessOfHistoryDepth() {
        UUID tenantA = tenant("PsBoundedTenantA").getId();
        Employee employeeA = employee(tenantA, "PsBoundedEmployee");
        for (int i = 0; i < 24; i++) {
            LocalDate periodStart = LocalDate.of(2024, 1, 1).plusMonths(i);
            PayrollPeriod p = period(tenantA, periodStart);
            PayrollRun r = run(tenantA, p);
            payslip(tenantA, employeeA, r, p, periodStart);
        }

        List<Payslip> result =
                payslips.findRecentForEmployee(tenantA, employeeA.getId(), PageRequest.of(0, 1));

        assertThat(result).hasSize(1);
    }

    @Test
    void
            findRecentForEmployeeNeverReturnsAPayslipFromADifferentTenantEvenWhenTheTenantIdIsCorrupted() {
        UUID tenantA = tenant("PsRecentTenantB1").getId();
        UUID tenantB = tenant("PsRecentTenantB2").getId();
        Employee employeeA = employee(tenantA, "PsRecentCrossEmployee");
        PayrollPeriod legitPeriod = period(tenantA, LocalDate.of(2025, 6, 1));
        PayrollRun legitRun = run(tenantA, legitPeriod);
        Payslip legitimate =
                payslip(tenantA, employeeA, legitRun, legitPeriod, LocalDate.of(2025, 6, 1));

        // Corrupted row: same employee, but persisted under tenant B's id — payslips.tenant_id
        // carries no FK, so this is directly constructible, unlike an Employee/tenant mismatch.
        // It is also dated later than the legitimate row, so if the tenant filter were a no-op
        // this corrupted row (not the legitimate one) would come back as "most recent".
        PayrollPeriod corruptPeriod = period(tenantB, LocalDate.of(2026, 6, 1));
        PayrollRun corruptRun = run(tenantB, corruptPeriod);
        payslip(tenantB, employeeA, corruptRun, corruptPeriod, LocalDate.of(2026, 6, 1));

        List<Payslip> result =
                payslips.findRecentForEmployee(tenantA, employeeA.getId(), PageRequest.of(0, 1));

        assertThat(result).extracting(Payslip::getId).containsExactly(legitimate.getId());
    }

    @Test
    void findAllForEmployeeInPeriodReturnsOnlyPayslipsWithinTheGivenDateWindow() {
        UUID tenantA = tenant("PsYearTenantA").getId();
        Employee employeeA = employee(tenantA, "PsYearEmployee");
        PayrollPeriod inYearPeriod = period(tenantA, LocalDate.of(2026, 3, 1));
        PayrollPeriod priorYearPeriod = period(tenantA, LocalDate.of(2025, 3, 1));
        PayrollRun inYearRun = run(tenantA, inYearPeriod);
        PayrollRun priorYearRun = run(tenantA, priorYearPeriod);
        Payslip inYear =
                payslip(tenantA, employeeA, inYearRun, inYearPeriod, LocalDate.of(2026, 3, 1));
        payslip(tenantA, employeeA, priorYearRun, priorYearPeriod, LocalDate.of(2025, 3, 1));

        List<Payslip> result =
                payslips.findAllForEmployeeInPeriod(
                        tenantA,
                        employeeA.getId(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31));

        assertThat(result).extracting(Payslip::getId).containsExactly(inYear.getId());
    }

    @Test
    void
            findAllForEmployeeInPeriodNeverReturnsAPayslipFromADifferentTenantEvenWhenTheTenantIdIsCorrupted() {
        UUID tenantA = tenant("PsYearTenantB1").getId();
        UUID tenantB = tenant("PsYearTenantB2").getId();
        Employee employeeA = employee(tenantA, "PsYearCrossEmployee");
        PayrollPeriod legitPeriod = period(tenantA, LocalDate.of(2026, 4, 1));
        PayrollRun legitRun = run(tenantA, legitPeriod);
        Payslip legitimate =
                payslip(tenantA, employeeA, legitRun, legitPeriod, LocalDate.of(2026, 4, 1));

        PayrollPeriod corruptPeriod = period(tenantB, LocalDate.of(2026, 5, 1));
        PayrollRun corruptRun = run(tenantB, corruptPeriod);
        payslip(tenantB, employeeA, corruptRun, corruptPeriod, LocalDate.of(2026, 5, 1));

        List<Payslip> result =
                payslips.findAllForEmployeeInPeriod(
                        tenantA,
                        employeeA.getId(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31));

        assertThat(result).extracting(Payslip::getId).containsExactly(legitimate.getId());
    }
}
