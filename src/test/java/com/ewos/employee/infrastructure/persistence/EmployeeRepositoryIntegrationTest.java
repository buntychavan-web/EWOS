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

/*
 * Sprint 27C fix-round (F5) additions below extend this same class rather than a new file, since
 * it already carries the tenant()/employee() fixture helpers every one of these needs.
 */

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

    /**
     * {@code findDirectReportsAfterCursor} orders by the real {@code display_name} column, which
     * {@link #employee(UUID, String, Employee)} above leaves {@code null} — the cursor tests need
     * it set explicitly to exercise real ordering rather than every row tying on {@code
     * coalesce(..., '')}.
     */
    private Employee employeeWithDisplayName(UUID tenantId, String displayName, Employee manager) {
        Employee e = employee(tenantId, displayName, manager);
        e.setDisplayName(displayName);
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
        //
        // The query filters on the returned employee's own tenantId, not the tenant of the
        // manager it points to — so the meaningful defense-in-depth case is querying the
        // *manager's* tenant (tenantA) while a corrupted row in a different tenant (tenantB)
        // also points its manager_employee_id at that same manager. A legitimate tenantA report
        // is included alongside the corrupted pointer so the assertion proves the tenant filter
        // is doing real work — excluding one matching-manager row while including another —
        // rather than the page simply happening to be empty.
        UUID tenantA = tenant("TenantA2").getId();
        UUID tenantB = tenant("TenantB2").getId();
        Employee managerA = employee(tenantA, "ManagerA2", null);
        Employee legitimateReport = employee(tenantA, "LegitimateReport", managerA);
        Employee crossTenantReport = employee(tenantB, "CrossTenantReport", managerA);

        var page =
                employees.findAllByTenantIdAndManagerId(
                        tenantA, managerA.getId(), PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(Employee::getId)
                .containsExactly(legitimateReport.getId())
                .doesNotContain(crossTenantReport.getId());
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

    /**
     * Sprint 27C fix-round (F5/F1) — {@code countByTenantIdAndManagerId} backs the MSS dashboard's
     * true headcount (replacing the capped {@code findAllByTenantIdAndManagerId} list size). Same
     * tenant-isolation defense-in-depth as {@link
     * #neverReturnsAReportFromADifferentTenantEvenIfTheManagerIdMatches}: a corrupted cross-tenant
     * report pointing at the same manager id must never inflate tenant A's count.
     */
    @Test
    void countByTenantIdAndManagerIdCountsTheTrueHeadcountEvenBeyondTheListPageCap() {
        UUID tenantA = tenant("CountTenantA").getId();
        Employee managerA = employee(tenantA, "CountManagerA", null);
        for (int i = 0; i < 205; i++) {
            employee(tenantA, "CountReport" + i, managerA);
        }

        long headcount = employees.countByTenantIdAndManagerId(tenantA, managerA.getId());

        assertThat(headcount).isEqualTo(205L);
    }

    @Test
    void
            countByTenantIdAndManagerIdNeverCountsAReportFromADifferentTenantEvenIfTheManagerIdMatches() {
        UUID tenantA = tenant("CountTenantB1").getId();
        UUID tenantB = tenant("CountTenantB2").getId();
        Employee managerA = employee(tenantA, "CountManagerB", null);
        employee(tenantA, "CountLegitReport", managerA);
        // Corrupted cross-tenant pointer: an employee actually persisted under tenant B, whose
        // manager.id happens to point at a manager from tenant A. The tenant filter is keyed on
        // the *returned row's own* tenant_id, not the manager's tenant — so querying tenant B
        // legitimately counts this row (it belongs to tenant B), while querying tenant A must
        // NOT count it. The meaningful assertion is headcountA staying at 1 despite a
        // manager-id-matching row existing elsewhere; headcountB=1 just confirms the corrupted
        // row was actually persisted the way this test intends.
        employee(tenantB, "CountCrossTenantReport", managerA);

        long headcountA = employees.countByTenantIdAndManagerId(tenantA, managerA.getId());
        long headcountB = employees.countByTenantIdAndManagerId(tenantB, managerA.getId());

        assertThat(headcountA).isEqualTo(1L);
        assertThat(headcountB).isEqualTo(1L);
    }

    /**
     * Sprint 27C fix-round (F5/F3) — {@code findDirectReportsAfterCursor} against the real
     * database: keyset ordering by {@code (displayName, id)}, first-page/next-page behavior, a
     * display-name tie broken by employee id, and the same cross-tenant defense-in-depth as every
     * other query in this file.
     */
    @Test
    void findDirectReportsAfterCursorReturnsReportsInDisplayNameThenIdOrder() {
        UUID tenantA = tenant("CursorTenantA").getId();
        Employee manager = employee(tenantA, "CursorManagerA", null);
        Employee bob = employeeWithDisplayName(tenantA, "Bob", manager);
        Employee alice = employeeWithDisplayName(tenantA, "Alice", manager);
        Employee carol = employeeWithDisplayName(tenantA, "Carol", manager);

        List<Employee> firstPage =
                employees.findDirectReportsAfterCursor(
                        tenantA, manager.getId(), null, null, PageRequest.of(0, 20));

        assertThat(firstPage)
                .extracting(Employee::getId)
                .containsExactly(alice.getId(), bob.getId(), carol.getId());
    }

    @Test
    void
            findDirectReportsAfterCursorTheNextPageStartsStrictlyAfterTheCursorWithNoDuplicatesOrGaps() {
        UUID tenantA = tenant("CursorTenantB").getId();
        Employee manager = employee(tenantA, "CursorManagerB", null);
        Employee alice = employeeWithDisplayName(tenantA, "Alice", manager);
        Employee bob = employeeWithDisplayName(tenantA, "Bob", manager);
        Employee carol = employeeWithDisplayName(tenantA, "Carol", manager);

        List<Employee> firstPage =
                employees.findDirectReportsAfterCursor(
                        tenantA, manager.getId(), null, null, PageRequest.of(0, 2));
        assertThat(firstPage)
                .extracting(Employee::getId)
                .containsExactly(alice.getId(), bob.getId());

        Employee lastOfFirstPage = firstPage.get(firstPage.size() - 1);
        List<Employee> secondPage =
                employees.findDirectReportsAfterCursor(
                        tenantA,
                        manager.getId(),
                        lastOfFirstPage.getDisplayName(),
                        lastOfFirstPage.getId(),
                        PageRequest.of(0, 2));

        assertThat(secondPage).extracting(Employee::getId).containsExactly(carol.getId());
    }

    @Test
    void findDirectReportsAfterCursorBreaksATiedDisplayNameByEmployeeId() {
        UUID tenantA = tenant("CursorTenantC").getId();
        Employee manager = employee(tenantA, "CursorManagerC", null);
        Employee first = employeeWithDisplayName(tenantA, "Same", manager);
        Employee second = employeeWithDisplayName(tenantA, "Same", manager);
        // java.util.UUID#compareTo compares mostSigBits/leastSigBits as *signed* longs, but
        // PostgreSQL's uuid type orders by unsigned byte comparison of the 16 raw bytes — the two
        // disagree whenever a UUID's first byte has its high bit set. The canonical string form
        // compares identically to Postgres's byte order (each hex pair encodes one byte, and hex
        // digits compare in byte-value order), so it — not UUID#compareTo — is what predicts the
        // query's actual ordering here.
        List<UUID> expectedOrder =
                first.getId().toString().compareTo(second.getId().toString()) < 0
                        ? List.of(first.getId(), second.getId())
                        : List.of(second.getId(), first.getId());

        List<Employee> firstPage =
                employees.findDirectReportsAfterCursor(
                        tenantA, manager.getId(), null, null, PageRequest.of(0, 20));

        assertThat(firstPage).extracting(Employee::getId).containsExactlyElementsOf(expectedOrder);
    }

    @Test
    void
            findDirectReportsAfterCursorNeverReturnsAReportFromADifferentTenantEvenIfTheManagerIdMatches() {
        UUID tenantA = tenant("CursorTenantD1").getId();
        UUID tenantB = tenant("CursorTenantD2").getId();
        Employee managerA = employee(tenantA, "CursorManagerD", null);
        Employee legitimateReport = employeeWithDisplayName(tenantA, "LegitCursorReport", managerA);
        employeeWithDisplayName(tenantB, "CrossTenantCursorReport", managerA);

        List<Employee> page =
                employees.findDirectReportsAfterCursor(
                        tenantA, managerA.getId(), null, null, PageRequest.of(0, 20));

        assertThat(page).extracting(Employee::getId).containsExactly(legitimateReport.getId());
    }
}
