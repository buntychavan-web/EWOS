package com.ewos.leave.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27C fix-round (F5) — {@code countByTenantIdAndStatusAndManagerId} against the real
 * database, mirroring {@code TimesheetRepositoryIntegrationTest}'s and {@code
 * EmployeeRepositoryIntegrationTest}'s tenant-isolation proof exactly: a manager's pending-leave
 * count is only ever computed from the tenant it actually belongs to, never leaked across tenants
 * even when a report row happens to carry the same manager id.
 */
@Transactional
class LeaveRequestRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired LeaveRequestRepository leaveRequests;
    @Autowired EmployeeRepository employees;
    @Autowired LeaveTypeRepository leaveTypes;
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

    private LeaveType leaveType(UUID tenantId) {
        LeaveType t = new LeaveType();
        t.setTenantId(tenantId);
        t.setCode("LR_IT_" + SEQ.incrementAndGet());
        t.setName("Integration Test Leave Type");
        t.setPaid(true);
        t.setAccrualDaysPerYear(BigDecimal.ZERO);
        return leaveTypes.save(t);
    }

    private LeaveRequest leaveRequest(
            UUID tenantId, Employee employee, LeaveType type, LeaveRequestStatus status) {
        return leaveRequest(tenantId, employee, type, status, LocalDate.of(2026, 8, 1));
    }

    private LeaveRequest leaveRequest(
            UUID tenantId,
            Employee employee,
            LeaveType type,
            LeaveRequestStatus status,
            LocalDate startDate) {
        LeaveRequest r = new LeaveRequest();
        r.setTenantId(tenantId);
        r.setCompanyId(COMPANY_ID);
        r.setEmployee(employee);
        r.setLeaveType(type);
        r.setStartDate(startDate);
        r.setEndDate(startDate.plusDays(1));
        r.setDaysRequested(new BigDecimal("2"));
        r.setStatus(status);
        if (status == LeaveRequestStatus.APPROVED) {
            r.setApprovedAt(java.time.Instant.now());
            r.setApprovedBy(UUID.randomUUID());
        }
        if (status == LeaveRequestStatus.REJECTED) {
            r.setRejectedAt(java.time.Instant.now());
            r.setRejectedBy(UUID.randomUUID());
        }
        return leaveRequests.save(r);
    }

    @Test
    void countByTenantIdAndStatusAndManagerIdCountsOnlySubmittedLeaveRequestsWithinTheTenant() {
        UUID tenantA = tenant("LrCountTenantA").getId();
        LeaveType type = leaveType(tenantA);
        Employee managerA = employee(tenantA, "LrCountManagerA", null);
        Employee report1 = employee(tenantA, "LrCountReport1", managerA);
        Employee report2 = employee(tenantA, "LrCountReport2", managerA);
        leaveRequest(tenantA, report1, type, LeaveRequestStatus.SUBMITTED);
        leaveRequest(
                tenantA, report2, type, LeaveRequestStatus.SUBMITTED, LocalDate.of(2026, 9, 1));
        leaveRequest(tenantA, report1, type, LeaveRequestStatus.APPROVED, LocalDate.of(2026, 7, 1));

        long count =
                leaveRequests.countByTenantIdAndStatusAndManagerId(
                        tenantA, LeaveRequestStatus.SUBMITTED, managerA.getId());

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void
            countByTenantIdAndStatusAndManagerIdNeverCountsALeaveRequestFromADifferentTenantEvenIfTheManagerIdMatches() {
        UUID tenantA = tenant("LrCountTenantB1").getId();
        UUID tenantB = tenant("LrCountTenantB2").getId();
        LeaveType typeA = leaveType(tenantA);
        LeaveType typeB = leaveType(tenantB);
        Employee managerA = employee(tenantA, "LrCountManagerB", null);
        Employee legitimateReport = employee(tenantA, "LrCountLegitReport", managerA);
        leaveRequest(tenantA, legitimateReport, typeA, LeaveRequestStatus.SUBMITTED);

        // Corrupted cross-tenant pointer, constructed directly at the repository level (bypassing
        // service-layer validation) to prove the query itself, not just the service, never leaks
        // data across tenants — same defense-in-depth rationale as the analogous Employee/Timesheet
        // integration tests.
        Employee crossTenantReportEmployee =
                employee(tenantB, "LrCountCrossTenantReport", managerA);
        leaveRequest(tenantB, crossTenantReportEmployee, typeB, LeaveRequestStatus.SUBMITTED);

        long countA =
                leaveRequests.countByTenantIdAndStatusAndManagerId(
                        tenantA, LeaveRequestStatus.SUBMITTED, managerA.getId());

        assertThat(countA).isEqualTo(1L);
    }
}
