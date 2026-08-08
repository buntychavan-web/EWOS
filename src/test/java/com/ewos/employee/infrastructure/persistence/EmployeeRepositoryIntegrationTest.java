package com.ewos.employee.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.tenancy.domain.Tenant;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27A (MSS foundation) — {@code findAllByTenantIdAndManagerId} against the real database and
 * the partial index on {@code manager_employee_id} (V10), including the tenant-isolation
 * defense-in-depth the query itself provides (PRD §12/finding 4.1): a report is only ever returned
 * for the tenant it actually belongs to, even if a corrupted {@code manager_employee_id} pointed
 * across tenants.
 *
 * <p>{@code employees.tenant_id} carries a real foreign key ({@code fk_employees_tenant}, V34) to
 * {@code tenants}, so — same as every other integration test in this codebase that persists an
 * {@link Employee} (see {@code TimeEntryRepositoryIntegrationTest}'s comment) — a tenant used here
 * must be a real, persisted row. This test needs two distinct tenants to prove cross-tenant
 * isolation, so it creates its own via {@link #tenant(String)} rather than reusing the single
 * seeded bootstrap tenant every other test relies on.
 */
@Transactional
class EmployeeRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

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
        e.setWorkEmail(label.toLowerCase(java.util.Locale.ROOT) + SEQ.get() + "@example.com");
        e.setHireDate(LocalDate.of(2024, 1, 1));
        e.setStatus(EmployeeStatus.ACTIVE);
        e.setManager(manager);
        return employees.save(e);
    }

    @Test
    void returnsOnlyDirectReportsOfTheGivenManagerWithinTheTenant() {
        UUID tenantA = tenant("TenantA").getId();
        UUID tenantB = tenant("TenantB").getId();
        Employee managerA = employee(tenantA, "ManagerA", null);
        Employee report1 = employee(tenantA, "Report1", managerA);
        Employee report2 = employee(tenantA, "Report2", managerA);
        employee(tenantA, "Unrelated", null); // same tenant, different (no) manager
        Employee managerB = employee(tenantB, "ManagerB", null);
        employee(tenantB, "OtherTenantReport", managerB);

        List<UUID> ids =
                employees
                        .findAllByTenantIdAndManagerId(
                                tenantA, managerA.getId(), PageRequest.of(0, 20, Sort.by("id")))
                        .map(Employee::getId)
                        .toList();

        assertThat(ids).containsExactlyInAnyOrder(report1.getId(), report2.getId());
    }

    @Test
    void neverReturnsAReportFromADifferentTenantEvenIfTheManagerIdMatches() {
        // Defense-in-depth: construct the cross-tenant pointer directly at the repository level
        // (bypassing EmployeeLifecyclePolicy.assertValidManager, which already blocks this at the
        // service layer — see EmployeeLifecyclePolicyTest#managerFromDifferentTenantRejected) to
        // prove the *query itself* also never leaks data across tenants, not just the service.
        UUID tenantA = tenant("TenantA2").getId();
        UUID tenantB = tenant("TenantB2").getId();
        Employee managerA = employee(tenantA, "ManagerA2", null);
        Employee crossTenantReport = employee(tenantB, "CrossTenantReport", managerA);

        var page =
                employees.findAllByTenantIdAndManagerId(
                        tenantB, managerA.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).doesNotContain(crossTenantReport).isEmpty();
    }

    @Test
    void resultsArePaginated() {
        UUID tenantA = tenant("TenantA3").getId();
        Employee manager = employee(tenantA, "BigTeamManager", null);
        for (int i = 0; i < 5; i++) {
            employee(tenantA, "TeamMember" + i, manager);
        }

        var firstPage =
                employees.findAllByTenantIdAndManagerId(
                        tenantA, manager.getId(), PageRequest.of(0, 2, Sort.by("employeeNumber")));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }
}
