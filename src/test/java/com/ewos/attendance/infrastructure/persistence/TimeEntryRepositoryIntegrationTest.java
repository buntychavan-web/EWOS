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

    @Test
    void findRecentForEmployeeIsCappedEvenWithFarMoreRowsInHistory() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

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
        // real clock-event history would look like.
        Instant now = Instant.now();
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
