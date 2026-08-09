package com.ewos.employee.api;

import com.ewos.employee.api.dto.EssCalendarResponse;
import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.application.EssCalendarService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27C — Upcoming Calendar (PRD §4.7). {@code employeeId} is always resolved from {@link
 * EmployeeContext#currentEmployeeId()}, never accepted as a parameter.
 */
@RestController
@RequestMapping("/api/v1/self-service/calendar")
@Tag(
        name = "Upcoming Calendar",
        description = "Merged holidays, approved leave, and timesheet deadlines for the caller")
public class EssCalendarController {

    private final EssCalendarService calendar;
    private final EmployeeContext employeeContext;
    private final TenantContext tenantContext;

    public EssCalendarController(
            EssCalendarService calendar,
            EmployeeContext employeeContext,
            TenantContext tenantContext) {
        this.calendar = calendar;
        this.employeeContext = employeeContext;
        this.tenantContext = tenantContext;
    }

    @GetMapping
    @Operation(summary = "Upcoming holidays, approved leave, and timesheet deadlines")
    public EssCalendarResponse upcoming(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID employeeId =
                employeeContext
                        .currentEmployeeId()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "No employee record is linked to your account"));
        return calendar.upcoming(tenantId, employeeId, from, to);
    }
}
