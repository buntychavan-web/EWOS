package com.ewos.employee.application;

import com.ewos.attendance.domain.Holiday;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.attendance.infrastructure.persistence.HolidayRepository;
import com.ewos.attendance.infrastructure.persistence.TimesheetRepository;
import com.ewos.employee.api.dto.CalendarEventResponse;
import com.ewos.employee.api.dto.EssCalendarResponse;
import com.ewos.employee.domain.CalendarEventType;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27C — Upcoming Calendar (PRD §4.7): merges holidays, the caller's own approved leave, and
 * their own not-yet-submitted timesheet deadlines into one sorted event list.
 *
 * <p>Holiday scoping reuses {@link HolidayRepository#findEffectiveForCompany} as-is rather than the
 * raw {@code holiday_date BETWEEN :from AND :to} SQL the original PRD draft proposed against a
 * table that doesn't exist ({@code attendance_holidays}) — the real {@code holidays} table already
 * has company scoping and a {@code recurringAnnually} flag; {@link Holiday#fallsOn} is the only
 * thing that correctly expands a recurring holiday across a date range (a stored 2024 date matched
 * by month/day, not a literal BETWEEN comparison). See the migration/PR notes for the full
 * discrepancy this resolves.
 */
@Service
@Transactional(readOnly = true)
public class EssCalendarService {

    private final EmployeeRepository employees;
    private final HolidayRepository holidays;
    private final LeaveRequestRepository leaveRequests;
    private final TimesheetRepository timesheets;

    public EssCalendarService(
            EmployeeRepository employees,
            HolidayRepository holidays,
            LeaveRequestRepository leaveRequests,
            TimesheetRepository timesheets) {
        this.employees = employees;
        this.holidays = holidays;
        this.leaveRequests = leaveRequests;
        this.timesheets = timesheets;
    }

    public EssCalendarResponse upcoming(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "'to' must be on or after 'from'");
        }
        Employee employee =
                employees
                        .findByIdAndTenantId(employeeId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No employee record is linked to your account"));

        List<CalendarEventResponse> events = new ArrayList<>();
        events.addAll(holidayEvents(tenantId, employee.getCompanyId(), from, to));
        events.addAll(leaveEvents(tenantId, employeeId, from, to));
        events.addAll(timesheetDueEvents(tenantId, employeeId, from, to));
        events.sort(Comparator.comparing(CalendarEventResponse::date));
        return new EssCalendarResponse(events);
    }

    private List<CalendarEventResponse> holidayEvents(
            UUID tenantId, UUID companyId, LocalDate from, LocalDate to) {
        List<Holiday> effective = holidays.findEffectiveForCompany(tenantId, companyId);
        List<CalendarEventResponse> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            for (Holiday h : effective) {
                if (h.fallsOn(date)) {
                    result.add(
                            new CalendarEventResponse(
                                    date, CalendarEventType.HOLIDAY, h.getName(), null));
                }
            }
        }
        return result;
    }

    private List<CalendarEventResponse> leaveEvents(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        return leaveRequests.findApprovedOverlapping(tenantId, employeeId, from, to).stream()
                .map(
                        r ->
                                new CalendarEventResponse(
                                        clamp(r.getStartDate(), from, to),
                                        CalendarEventType.LEAVE,
                                        leaveTitle(r),
                                        Map.of("leaveRequestId", r.getId().toString())))
                .toList();
    }

    private List<CalendarEventResponse> timesheetDueEvents(
            UUID tenantId, UUID employeeId, LocalDate from, LocalDate to) {
        return timesheets.findAllForEmployee(tenantId, employeeId).stream()
                .filter(t -> t.getStatus() == TimesheetStatus.DRAFT)
                .filter(t -> !t.getPeriodEnd().isBefore(from) && !t.getPeriodEnd().isAfter(to))
                .map(
                        t ->
                                new CalendarEventResponse(
                                        t.getPeriodEnd(),
                                        CalendarEventType.TIMESHEET_DUE,
                                        "Timesheet due",
                                        Map.of("periodEnd", t.getPeriodEnd().toString())))
                .toList();
    }

    private static String leaveTitle(LeaveRequest r) {
        return r.getLeaveType() != null ? r.getLeaveType().getName() : "Leave";
    }

    private static LocalDate clamp(LocalDate date, LocalDate from, LocalDate to) {
        if (date.isBefore(from)) {
            return from;
        }
        return date.isAfter(to) ? to : date;
    }
}
