package com.ewos.attendance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27B — {@code findAllByTenantIdAndStatusAndManagerId} against the real database, mirroring
 * {@code EmployeeRepositoryIntegrationTest}'s tenant-isolation proof exactly: a manager's pending
 * timesheet is only ever returned for the tenant it actually belongs to, never leaked across
 * tenants even when a report row happens to carry the same manager id.
 */
@Transactional
class TimesheetRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired TimesheetRepository timesheets;
    @Autowired EmployeeRepository employees;
    @Autowired TenantRepository tenants;

    private Tenant tenant(String label) {
        Tenant t = new Tenant();
        t.setCode(label + "-" + SEQ.incrementAndGet());
        t.setName(label);
        return tenants.save(t);
    }

    private Employee employee(UUID tenantId, String label, Employee manager) {
        Employee e = new Employee();
        e.setTenantId(tenantId);
        e.setCompanyId(COMPANY_ID);
        e.setEmployeeNumber(label + "-" + SEQ.incrementAndGet());
        e.setFirstName(label);
        e.setLastName("Report");
        e.setWorkEmail(label.toLowerCase(Locale.ROOT) + SEQ.get() + "@example.com");
        e.setHireDate(LocalDate.of(2024, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setManager(manager);
        return employees.save(e);
    }

    private Timesheet timesheet(UUID tenantId, Employee employee, TimesheetStatus status) {
        return timesheet(tenantId, employee, status, LocalDate.of(2026, 8, 1));
    }

    /**
     * Every non-DRAFT status here also has to satisfy the real DB check constraints from {@code
     * V12__attendance_engine.sql} ({@code ck_timesheets_submitted_has_ts}, {@code
     * ck_timesheets_approved_pair}, {@code ck_timesheets_rejected_pair}) — a plain Docker-less unit
     * test can't catch a missing companion column, so this helper sets every column each status
     * requires, not just the ones the query under test reads. {@code periodStart} is a parameter
     * (not hardcoded) because {@code ux_timesheets_employee_period_alive} allows only one alive
     * timesheet per employee per period — a test needing two rows for the same employee must use
     * two different periods.
     */
    private Timesheet timesheet(
            UUID tenantId, Employee employee, TimesheetStatus status, LocalDate periodStart) {
        Timesheet ts = new Timesheet();
        ts.setTenantId(tenantId);
        ts.setCompanyId(COMPANY_ID);
        ts.setEmployee(employee);
        ts.setPeriodStart(periodStart);
        ts.setPeriodEnd(periodStart.plusDays(6));
        ts.setStatus(status);
        if (status != TimesheetStatus.DRAFT) {
            ts.setSubmittedAt(Instant.now());
        }
        if (status == TimesheetStatus.APPROVED) {
            ts.setApprovedAt(Instant.now());
            ts.setApprovedBy(UUID.randomUUID());
        }
        if (status == TimesheetStatus.REJECTED) {
            ts.setRejectedAt(Instant.now());
            ts.setRejectedBy(UUID.randomUUID());
        }
        return timesheets.save(ts);
    }

    @Test
    void returnsOnlySubmittedTimesheetsOfTheGivenManagerWithinTheTenant() {
        UUID tenantA = tenant("TsTenantA").getId();
        Employee managerA = employee(tenantA, "ManagerA", null);
        Employee report1 = employee(tenantA, "Report1", managerA);
        Employee report2 = employee(tenantA, "Report2", managerA);
        Timesheet pending1 = timesheet(tenantA, report1, TimesheetStatus.SUBMITTED);
        Timesheet pending2 = timesheet(tenantA, report2, TimesheetStatus.SUBMITTED);
        // Different period: ux_timesheets_employee_period_alive allows only one alive timesheet
        // per employee per period, so this "already decided" row for report1 can't reuse the
        // SUBMITTED one's period.
        timesheet(
                tenantA,
                report1,
                TimesheetStatus.APPROVED,
                LocalDate.of(2026, 7, 1)); // already decided — excluded

        List<UUID> ids =
                timesheets
                        .findAllByTenantIdAndStatusAndManagerId(
                                tenantA,
                                TimesheetStatus.SUBMITTED,
                                managerA.getId(),
                                PageRequest.of(0, 20, Sort.by("id")))
                        .map(Timesheet::getId)
                        .toList();

        assertThat(ids).containsExactlyInAnyOrder(pending1.getId(), pending2.getId());
    }

    @Test
    void neverReturnsATimesheetFromADifferentTenantEvenIfTheManagerIdMatches() {
        UUID tenantA = tenant("TsTenantB1").getId();
        UUID tenantB = tenant("TsTenantB2").getId();
        Employee managerA = employee(tenantA, "ManagerA2", null);
        Employee legitimateReport = employee(tenantA, "LegitReport", managerA);
        Timesheet legitimate = timesheet(tenantA, legitimateReport, TimesheetStatus.SUBMITTED);

        // Corrupted cross-tenant pointer, constructed directly at the repository level (bypassing
        // service-layer validation) to prove the query itself, not just the service, never leaks
        // data across tenants — same defense-in-depth rationale as
        // EmployeeRepositoryIntegrationTest#neverReturnsAReportFromADifferentTenantEvenIfTheManagerIdMatches.
        Employee crossTenantReportEmployee = employee(tenantB, "CrossTenantReport", managerA);
        timesheet(tenantB, crossTenantReportEmployee, TimesheetStatus.SUBMITTED);

        List<UUID> ids =
                timesheets
                        .findAllByTenantIdAndStatusAndManagerId(
                                tenantA,
                                TimesheetStatus.SUBMITTED,
                                managerA.getId(),
                                PageRequest.of(0, 20, Sort.by("id")))
                        .map(Timesheet::getId)
                        .toList();

        assertThat(ids).containsExactly(legitimate.getId());
    }
}
