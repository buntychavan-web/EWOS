package com.ewos.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.attendance.domain.AttendanceLopCalculator;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.domain.Holiday;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.domain.TimeEventType;
import com.ewos.attendance.infrastructure.persistence.AttendancePolicyRepository;
import com.ewos.attendance.infrastructure.persistence.HolidayRepository;
import com.ewos.attendance.infrastructure.persistence.TimeEntryRepository;
import com.ewos.employee.domain.Employee;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceLopServiceTest {

    @Mock AttendancePolicyRepository policies;
    @Mock TimeEntryRepository timeEntries;
    @Mock HolidayRepository holidays;

    private AttendanceLopService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final LocalDate periodStart = LocalDate.of(2026, 1, 5);
    private final LocalDate periodEnd = LocalDate.of(2026, 1, 9);

    @BeforeEach
    void setUp() {
        service =
                new AttendanceLopService(
                        policies,
                        timeEntries,
                        holidays,
                        new AttendanceLopCalculator(
                                new com.ewos.attendance.domain.TimesheetCalculator()));
    }

    @Test
    void returnsEmptyWhenCompanyHasNoAttendancePolicyConfigured() {
        when(policies.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of());

        Optional<Map<UUID, AttendanceLopCalculator.Result>> outcome =
                service.computeForRun(
                        tenantId, companyId, periodStart, periodEnd, List.of(employeeId), Map.of());

        assertThat(outcome).isEmpty();
        verify(timeEntries, never()).findForEmployeesInRange(any(), anyCollection(), any(), any());
    }

    @Test
    void returnsEmptyForAnEmptyEmployeeList() {
        Optional<Map<UUID, AttendanceLopCalculator.Result>> outcome =
                service.computeForRun(
                        tenantId, companyId, periodStart, periodEnd, List.of(), Map.of());

        assertThat(outcome).isEmpty();
        verify(policies, never()).findEffectiveForCompany(any(), any());
    }

    @Test
    void computesPerEmployeeLopWhenPolicyIsConfigured() {
        when(policies.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of(policy()));
        when(holidays.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of());
        Employee emp = new Employee();
        emp.setId(employeeId);
        TimeEntry in = entry(periodStart, 9, 0, TimeEventType.IN, emp);
        TimeEntry out = entry(periodStart, 17, 0, TimeEventType.OUT, emp);
        when(timeEntries.findForEmployeesInRange(
                        org.mockito.ArgumentMatchers.eq(tenantId), anyCollection(), any(), any()))
                .thenReturn(List.of(in, out));

        Optional<Map<UUID, AttendanceLopCalculator.Result>> outcome =
                service.computeForRun(
                        tenantId, companyId, periodStart, periodEnd, List.of(employeeId), Map.of());

        assertThat(outcome).isPresent();
        AttendanceLopCalculator.Result result = outcome.get().get(employeeId);
        assertThat(result).isNotNull();
        // Present on periodStart (Mon), absent Tue-Fri (no entries), weekend already excluded.
        assertThat(result.lopDays()).isEqualByComparingTo("4");
    }

    @Test
    void holidayInRangeSuppressesWhatWouldOtherwiseBeAnAbsence() {
        when(policies.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of(policy()));
        Holiday holiday = new Holiday();
        holiday.setHolidayDate(periodStart);
        holiday.setRecurringAnnually(false);
        when(holidays.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of(holiday));
        when(timeEntries.findForEmployeesInRange(any(), anyCollection(), any(), any()))
                .thenReturn(List.of());

        Optional<Map<UUID, AttendanceLopCalculator.Result>> outcome =
                service.computeForRun(
                        tenantId,
                        companyId,
                        periodStart,
                        periodStart,
                        List.of(employeeId),
                        Map.of());

        assertThat(outcome.get().get(employeeId).lopDays()).isEqualByComparingTo("0");
    }

    @Test
    void leaveDatesForAnEmployeeAreExcludedFromAttendanceLop() {
        when(policies.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of(policy()));
        when(holidays.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of());
        when(timeEntries.findForEmployeesInRange(any(), anyCollection(), any(), any()))
                .thenReturn(List.of());

        Optional<Map<UUID, AttendanceLopCalculator.Result>> outcome =
                service.computeForRun(
                        tenantId,
                        companyId,
                        periodStart,
                        periodStart,
                        List.of(employeeId),
                        Map.of(employeeId, Set.of(periodStart)));

        assertThat(outcome.get().get(employeeId).lopDays()).isEqualByComparingTo("0");
    }

    private static AttendancePolicy policy() {
        AttendancePolicy p = new AttendancePolicy();
        p.setStandardHoursPerDay(new java.math.BigDecimal("8.00"));
        p.setStandardHoursPerWeek(new java.math.BigDecimal("40.00"));
        p.setWorkingDays("MON,TUE,WED,THU,FRI");
        return p;
    }

    private static TimeEntry entry(
            LocalDate day, int hour, int minute, TimeEventType type, Employee employee) {
        TimeEntry e = new TimeEntry();
        e.setEventType(type);
        e.setEmployee(employee);
        Instant t =
                LocalDateTime.of(day, java.time.LocalTime.of(hour, minute))
                        .toInstant(ZoneOffset.UTC);
        e.setOccurredAt(t);
        return e;
    }
}
