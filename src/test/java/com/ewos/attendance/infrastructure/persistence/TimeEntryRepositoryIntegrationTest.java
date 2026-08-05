package com.ewos.attendance.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.domain.TimeEventType;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Real-database coverage for {@link TimeEntryRepository#findRecentForEmployee} — proves the {@code
 * limit} clause actually caps rows returned by Postgres/Hibernate, not just something assumed from
 * reading the JPQL. A mock-based unit test (see {@code TimeEntryServiceTest}) cannot exercise this:
 * it stubs the repository method itself.
 */
class TimeEntryRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TimeEntryRepository entries;
    @Autowired private EmployeeRepository employees;

    // The seeded bootstrap tenant/company (IdentityBootstrap) — employees.tenant_id and
    // employees.company_id are both real foreign keys, so an arbitrary UUID.randomUUID() fails
    // with a constraint violation; every other integration test in this codebase that persists
    // an Employee reuses this same well-known id (see PayrollRunControllerIntegrationTest).
    private static final UUID DEFAULT_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEFAULT_COMPANY_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void findRecentForEmployeeIsCappedEvenWithFarMoreRowsInHistory() {
        UUID tenantId = DEFAULT_TENANT_ID;
        UUID companyId = DEFAULT_COMPANY_ID;

        Employee employee = new Employee();
        employee.setTenantId(tenantId);
        employee.setCompanyId(companyId);
        employee.setEmployeeNumber("RECENT-CAP-" + UUID.randomUUID());
        employee.setFirstName("Recent");
        employee.setLastName("Cap");
        employee.setWorkEmail("recent.cap." + UUID.randomUUID() + "@bench.example");
        employee.setHireDate(LocalDate.of(2015, 1, 1));
        employee.setStatus(EmployeeStatus.ACTIVE);
        employee = employees.save(employee);

        // Seed well beyond RECENT_LIMIT rows spread over a decade, as a long-tenured employee's
        // real clock-event history would look like. Truncated to microseconds: Postgres
        // TIMESTAMPTZ (and the JDBC round trip) only preserves microsecond precision, so an
        // untruncated Instant.now() (nanosecond precision) would never compare equal to the value
        // read back after a save/fetch cycle.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        int totalRows = TimeEntryRepository.RECENT_LIMIT + 25;
        for (int i = 0; i < totalRows; i++) {
            TimeEntry entry = new TimeEntry();
            entry.setTenantId(tenantId);
            entry.setCompanyId(companyId);
            entry.setEmployee(employee);
            entry.setEventType(i % 2 == 0 ? TimeEventType.IN : TimeEventType.OUT);
            entry.setOccurredAt(now.minus(i, ChronoUnit.DAYS));
            entries.save(entry);
        }

        List<TimeEntry> recent = entries.findRecentForEmployee(tenantId, employee.getId());

        assertThat(recent).hasSize(TimeEntryRepository.RECENT_LIMIT);
        // Most-recent-first: the first row must be the one seeded with i=0 (now), not an
        // arbitrarily-ordered subset of the full history.
        assertThat(recent.get(0).getOccurredAt()).isEqualTo(now);
        assertThat(recent.get(TimeEntryRepository.RECENT_LIMIT - 1).getOccurredAt())
                .isEqualTo(now.minus(TimeEntryRepository.RECENT_LIMIT - 1, ChronoUnit.DAYS));
    }
}
