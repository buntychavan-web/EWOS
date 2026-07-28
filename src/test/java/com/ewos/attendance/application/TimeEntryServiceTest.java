package com.ewos.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.CreateTimeEntryRequest;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.domain.TimeEntrySource;
import com.ewos.attendance.domain.TimeEventType;
import com.ewos.attendance.infrastructure.persistence.TimeEntryRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

/** Raw clock-event recording: cross-company guard, default source, and the correction chain. */
@ExtendWith(MockitoExtension.class)
class TimeEntryServiceTest {

    @Mock TimeEntryRepository entries;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private TimeEntryService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new TimeEntryService(entries, employees, new AttendanceMapper(), events, guard);
        org.mockito.Mockito.lenient()
                .when(entries.save(any(TimeEntry.class)))
                .thenAnswer(
                        inv -> {
                            TimeEntry e = inv.getArgument(0);
                            if (e.getId() == null) {
                                e.setId(UUID.randomUUID());
                            }
                            return e;
                        });
    }

    private Employee employeeIn(UUID company) {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setCompanyId(company);
        return e;
    }

    private CreateTimeEntryRequest request(TimeEntrySource source, UUID correctionOf) {
        return new CreateTimeEntryRequest(
                tenantId,
                companyId,
                employeeId,
                TimeEventType.IN,
                Instant.parse("2026-03-01T09:00:00Z"),
                source,
                "HQ",
                null,
                correctionOf);
    }

    @Test
    void recordRejectedWhenEmployeeDoesNotExist() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(request(null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Employee not found");
    }

    @Test
    void recordRejectedWhenEmployeeBelongsToADifferentCompany() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(UUID.randomUUID())));

        assertThatThrownBy(() -> service.record(request(null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different company");
    }

    @Test
    void recordDefaultsSourceToManualWhenNotSupplied() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));

        var response = service.record(request(null, null));

        assertThat(response.source()).isEqualTo(TimeEntrySource.MANUAL);
    }

    @Test
    void recordHonorsAnExplicitlySuppliedSource() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));

        var response = service.record(request(TimeEntrySource.BADGE, null));

        assertThat(response.source()).isEqualTo(TimeEntrySource.BADGE);
    }

    @Test
    void recordRejectedWhenTheOriginalEntryToCorrectDoesNotExist() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        UUID originalId = UUID.randomUUID();
        when(entries.findByIdAndTenantId(originalId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.record(request(null, originalId)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Original entry to correct not found");
    }

    @Test
    void recordForcesSourceToCorrectionWhenCorrectingAnEarlierEntryRegardlessOfSuppliedSource() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employeeIn(companyId)));
        UUID originalId = UUID.randomUUID();
        TimeEntry original = new TimeEntry();
        original.setId(originalId);
        when(entries.findByIdAndTenantId(originalId, tenantId)).thenReturn(Optional.of(original));

        // Even though MANUAL is explicitly requested, correcting an entry always yields CORRECTION.
        var response = service.record(request(TimeEntrySource.MANUAL, originalId));

        assertThat(response.source()).isEqualTo(TimeEntrySource.CORRECTION);
        assertThat(response.correctionOf()).isEqualTo(originalId);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownEntry() {
        UUID id = UUID.randomUUID();
        when(entries.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void forEmployeeInRangeChecksAccessAcrossEveryDistinctCompanyReturned() {
        TimeEntry e = new TimeEntry();
        e.setCompanyId(companyId);
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-31T23:59:59Z");
        when(entries.findForEmployeeInRange(tenantId, employeeId, from, to)).thenReturn(List.of(e));

        service.forEmployeeInRange(tenantId, employeeId, from, to);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void recentForEmployeeChecksAccessAcrossEveryDistinctCompanyReturned() {
        TimeEntry e = new TimeEntry();
        e.setCompanyId(companyId);
        when(entries.findRecentForEmployee(tenantId, employeeId)).thenReturn(List.of(e));

        service.recentForEmployee(tenantId, employeeId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }
}
