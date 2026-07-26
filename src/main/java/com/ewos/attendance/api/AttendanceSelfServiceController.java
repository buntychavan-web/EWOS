package com.ewos.attendance.api;

import com.ewos.attendance.api.dto.TimeEntryResponse;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.attendance.application.AttendanceSelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 3 — Employee Self-Service over Attendance. Requires only authentication (no ATT_*
 * permission); read-only, matching this sprint's scope (live clock-in/out capture is out of scope —
 * see the Sprint 3 SDD §14).
 */
@RestController
@RequestMapping("/api/v1/attendance/self-service")
@Tag(name = "Attendance Self-Service", description = "An employee's own time entries and timesheets")
public class AttendanceSelfServiceController {

    private final AttendanceSelfService service;

    public AttendanceSelfServiceController(AttendanceSelfService service) {
        this.service = service;
    }

    @GetMapping("/time-entries")
    @Operation(summary = "The caller's own recent time entries, most-recent-first")
    public List<TimeEntryResponse> myRecentTimeEntries() {
        return service.myRecentTimeEntries();
    }

    @GetMapping("/timesheets")
    @Operation(summary = "The caller's own timesheets")
    public List<TimesheetResponse> myTimesheets() {
        return service.myTimesheets();
    }
}
