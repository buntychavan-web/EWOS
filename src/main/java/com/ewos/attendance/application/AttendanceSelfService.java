package com.ewos.attendance.application;

import com.ewos.attendance.api.dto.TimeEntryResponse;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.employee.application.EmployeeContext;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sprint 3 — Employee Self-Service over Attendance. Delegates entirely to {@link TimeEntryService}
 * and {@link TimesheetService}; no new business logic. Mirrors {@link
 * com.ewos.leave.application.LeaveSelfService}'s structure.
 */
@Service
public class AttendanceSelfService {

    private final TimeEntryService timeEntries;
    private final TimesheetService timesheets;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public AttendanceSelfService(
            TimeEntryService timeEntries,
            TimesheetService timesheets,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.timeEntries = timeEntries;
        this.timesheets = timesheets;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    public List<TimeEntryResponse> myRecentTimeEntries() {
        return timeEntries.recentForEmployee(tenantContext.homeTenantId(), requireEmployeeId());
    }

    public List<TimesheetResponse> myTimesheets() {
        return timesheets.forEmployee(tenantContext.homeTenantId(), requireEmployeeId());
    }

    private UUID requireEmployeeId() {
        return employeeContext
                .currentEmployeeId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No employee record is linked to your account"));
    }
}
