package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.attendance.domain.Holiday;
import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.attendance.infrastructure.persistence.HolidayRepository;
import com.ewos.attendance.infrastructure.persistence.TimesheetRepository;
import com.ewos.employee.api.dto.EssCalendarResponse;
import com.ewos.employee.domain.CalendarEventType;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Sprint 27C — {@link EssCalendarService}: merge correctness, company-scoped + tenant-wide holiday
 * visibility (via {@code recurringAnnually}/{@code fallsOn}), and date-range filtering.
 */
@ExtendWith(MockitoExtension.class)
class EssCalendarServiceTest {

    @Mock EmployeeRepository employees;
    @Mock HolidayRepository holidays;
    @Mock LeaveRequestRepository leaveRequests;
    @Mock TimesheetRepository timesheets;

    private EssCalendarService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EssCalendarService(employees, holidays, leaveRequests, timesheets);
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setCompanyId(companyId);
        lenient()
                .when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee));
    }

    @Test
    void throws404WhenCallerHasNoLinkedEmployeeRecord() {
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.upcoming(
                                        tenantId,
                                        employeeId,
                                        LocalDate.of(2026, 1, 1),
                                        LocalDate.of(2026, 1, 31)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rejectsAToDateBeforeFromDate() {
        assertThatThrownBy(
                        () ->
                                service.upcoming(
                                        tenantId,
                                        employeeId,
                                        LocalDate.of(2026, 2, 1),
                                        LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void includesARecurringCompanyScopedHolidayThatFallsInsideTheRange() {
        Holiday recurring = new Holiday();
        recurring.setName("Republic Day");
        recurring.setHolidayDate(LocalDate.of(2020, 1, 26));
        recurring.setRecurringAnnually(true);
        recurring.setCompanyId(companyId);
        when(holidays.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of(recurring));
        when(leaveRequests.findApprovedOverlapping(
                        tenantId, employeeId, LocalDate.of(2026, 1, 20), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());

        EssCalendarResponse response =
                service.upcoming(
                        tenantId, employeeId, LocalDate.of(2026, 1, 20), LocalDate.of(2026, 1, 31));

        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).type()).isEqualTo(CalendarEventType.HOLIDAY);
        assertThat(response.events().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 26));
        assertThat(response.events().get(0).title()).isEqualTo("Republic Day");
    }

    @Test
    void excludesAHolidayOutsideTheRequestedRange() {
        Holiday farAway = new Holiday();
        farAway.setName("Some Other Day");
        farAway.setHolidayDate(LocalDate.of(2026, 6, 15));
        farAway.setRecurringAnnually(false);
        when(holidays.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of(farAway));
        when(leaveRequests.findApprovedOverlapping(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());

        EssCalendarResponse response =
                service.upcoming(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(response.events()).isEmpty();
    }

    @Test
    void mergesApprovedLeaveAndClampsItsStartDateIntoTheRequestedRange() {
        LeaveRequest request = new LeaveRequest();
        request.setId(UUID.randomUUID());
        LeaveType annual = new LeaveType();
        annual.setName("Annual Leave");
        request.setLeaveType(annual);
        request.setStartDate(LocalDate.of(2025, 12, 28));
        request.setEndDate(LocalDate.of(2026, 1, 3));
        when(holidays.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of());
        when(leaveRequests.findApprovedOverlapping(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10)))
                .thenReturn(List.of(request));
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of());

        EssCalendarResponse response =
                service.upcoming(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 10));

        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).type()).isEqualTo(CalendarEventType.LEAVE);
        assertThat(response.events().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(response.events().get(0).title()).isEqualTo("Annual Leave");
    }

    @Test
    void includesAnOpenDraftTimesheetWhosePeriodEndFallsInRangeButExcludesSubmittedOnes() {
        Timesheet draftInRange = new Timesheet();
        draftInRange.setStatus(TimesheetStatus.DRAFT);
        draftInRange.setPeriodStart(LocalDate.of(2026, 1, 1));
        draftInRange.setPeriodEnd(LocalDate.of(2026, 1, 31));
        Timesheet submitted = new Timesheet();
        submitted.setStatus(TimesheetStatus.SUBMITTED);
        submitted.setPeriodStart(LocalDate.of(2026, 1, 1));
        submitted.setPeriodEnd(LocalDate.of(2026, 1, 15));
        when(holidays.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of());
        when(leaveRequests.findApprovedOverlapping(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());
        when(timesheets.findAllForEmployee(tenantId, employeeId))
                .thenReturn(List.of(draftInRange, submitted));

        EssCalendarResponse response =
                service.upcoming(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(response.events()).hasSize(1);
        assertThat(response.events().get(0).type()).isEqualTo(CalendarEventType.TIMESHEET_DUE);
        assertThat(response.events().get(0).date()).isEqualTo(LocalDate.of(2026, 1, 31));
    }

    @Test
    void sortsMergedEventsByDateAscending() {
        Holiday laterHoliday = new Holiday();
        laterHoliday.setName("Later Holiday");
        laterHoliday.setHolidayDate(LocalDate.of(2026, 1, 20));
        laterHoliday.setRecurringAnnually(false);
        Timesheet earlierDue = new Timesheet();
        earlierDue.setStatus(TimesheetStatus.DRAFT);
        earlierDue.setPeriodStart(LocalDate.of(2026, 1, 1));
        earlierDue.setPeriodEnd(LocalDate.of(2026, 1, 5));
        when(holidays.findEffectiveForCompany(tenantId, companyId))
                .thenReturn(List.of(laterHoliday));
        when(leaveRequests.findApprovedOverlapping(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(List.of());
        when(timesheets.findAllForEmployee(tenantId, employeeId)).thenReturn(List.of(earlierDue));

        EssCalendarResponse response =
                service.upcoming(
                        tenantId, employeeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(response.events())
                .extracting(e -> e.date())
                .containsExactly(LocalDate.of(2026, 1, 5), LocalDate.of(2026, 1, 20));
    }
}
