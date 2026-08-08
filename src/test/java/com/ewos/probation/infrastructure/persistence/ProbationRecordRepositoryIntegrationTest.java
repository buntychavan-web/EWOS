package com.ewos.probation.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.probation.domain.ProbationRecord;
import com.ewos.probation.domain.ProbationStatus;
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
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27B — {@code findAllByTenantIdAndStatusAndManagerId} against the real database, mirroring
 * {@code TimesheetRepositoryIntegrationTest}'s (and, before that, {@code
 * EmployeeRepositoryIntegrationTest}'s) tenant-isolation proof exactly: a manager's pending
 * probation confirmation is only ever returned for the tenant it actually belongs to, never leaked
 * across tenants even when a report row happens to carry the same manager id.
 */
@Transactional
class ProbationRecordRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired ProbationRecordRepository records;
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

    private ProbationRecord record(UUID tenantId, Employee employee, ProbationStatus status) {
        ProbationRecord r = new ProbationRecord();
        r.setTenantId(tenantId);
        r.setCompanyId(COMPANY_ID);
        r.setEmployee(employee);
        r.setPeriodStart(LocalDate.of(2026, 1, 1));
        r.setPeriodEnd(LocalDate.of(2026, 4, 1));
        r.setStatus(status);
        return records.save(r);
    }

    @Test
    void returnsOnlyPendingApprovalRecordsOfTheGivenManagerWithinTheTenant() {
        UUID tenantA = tenant("PrTenantA").getId();
        Employee managerA = employee(tenantA, "ManagerA", null);
        Employee report1 = employee(tenantA, "Report1", managerA);
        Employee report2 = employee(tenantA, "Report2", managerA);
        ProbationRecord pending1 = record(tenantA, report1, ProbationStatus.PENDING_APPROVAL);
        ProbationRecord pending2 = record(tenantA, report2, ProbationStatus.PENDING_APPROVAL);
        record(tenantA, report1, ProbationStatus.IN_PROBATION); // not yet submitted — excluded

        List<UUID> ids =
                records.findAllByTenantIdAndStatusAndManagerId(
                                tenantA,
                                ProbationStatus.PENDING_APPROVAL,
                                managerA.getId(),
                                PageRequest.of(0, 20, Sort.by("id")))
                        .map(ProbationRecord::getId)
                        .toList();

        assertThat(ids).containsExactlyInAnyOrder(pending1.getId(), pending2.getId());
    }

    @Test
    void neverReturnsARecordFromADifferentTenantEvenIfTheManagerIdMatches() {
        UUID tenantA = tenant("PrTenantB1").getId();
        UUID tenantB = tenant("PrTenantB2").getId();
        Employee managerA = employee(tenantA, "ManagerA2", null);
        Employee legitimateReport = employee(tenantA, "LegitReport", managerA);
        ProbationRecord legitimate =
                record(tenantA, legitimateReport, ProbationStatus.PENDING_APPROVAL);

        // Corrupted cross-tenant pointer, constructed directly at the repository level (bypassing
        // service-layer validation) to prove the query itself, not just the service, never leaks
        // data across tenants — same defense-in-depth rationale as
        // EmployeeRepositoryIntegrationTest/TimesheetRepositoryIntegrationTest.
        Employee crossTenantReportEmployee = employee(tenantB, "CrossTenantReport", managerA);
        record(tenantB, crossTenantReportEmployee, ProbationStatus.PENDING_APPROVAL);

        List<UUID> ids =
                records.findAllByTenantIdAndStatusAndManagerId(
                                tenantA,
                                ProbationStatus.PENDING_APPROVAL,
                                managerA.getId(),
                                PageRequest.of(0, 20, Sort.by("id")))
                        .map(ProbationRecord::getId)
                        .toList();

        assertThat(ids).containsExactly(legitimate.getId());
    }
}
