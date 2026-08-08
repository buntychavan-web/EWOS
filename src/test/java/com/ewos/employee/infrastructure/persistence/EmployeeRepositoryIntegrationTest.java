package com.ewos.employee.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
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
 */
@Transactional
class EmployeeRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID COMPANY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired EmployeeRepository employees;

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
        Employee managerA = employee(TENANT_A, "ManagerA", null);
        Employee report1 = employee(TENANT_A, "Report1", managerA);
        Employee report2 = employee(TENANT_A, "Report2", managerA);
        employee(TENANT_A, "Unrelated", null); // same tenant, different (no) manager
        Employee managerB = employee(TENANT_B, "ManagerB", null);
        employee(TENANT_B, "OtherTenantReport", managerB);

        List<UUID> ids =
                employees
                        .findAllByTenantIdAndManagerId(
                                TENANT_A, managerA.getId(), PageRequest.of(0, 20, Sort.by("id")))
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
        Employee managerA = employee(TENANT_A, "ManagerA2", null);
        Employee crossTenantReport = employee(TENANT_B, "CrossTenantReport", managerA);

        var page =
                employees.findAllByTenantIdAndManagerId(
                        TENANT_B, managerA.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent()).doesNotContain(crossTenantReport).isEmpty();
    }

    @Test
    void resultsArePaginated() {
        Employee manager = employee(TENANT_A, "BigTeamManager", null);
        for (int i = 0; i < 5; i++) {
            employee(TENANT_A, "TeamMember" + i, manager);
        }

        var firstPage =
                employees.findAllByTenantIdAndManagerId(
                        TENANT_A, manager.getId(), PageRequest.of(0, 2, Sort.by("employeeNumber")));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(5);
        assertThat(firstPage.getTotalPages()).isEqualTo(3);
    }
}
