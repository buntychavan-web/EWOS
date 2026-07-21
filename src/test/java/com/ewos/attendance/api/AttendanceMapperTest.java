package com.ewos.attendance.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.attendance.api.dto.AttendancePolicyResponse;
import com.ewos.attendance.api.dto.TimeEntryResponse;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.domain.TimeEntrySource;
import com.ewos.attendance.domain.TimeEventType;
import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.employee.domain.Employee;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceMapperTest {

    private final AttendanceMapper mapper = new AttendanceMapper();

    @Test
    void policyMapsAllFields() {
        AttendancePolicy p = new AttendancePolicy();
        p.setId(UUID.randomUUID());
        p.setTenantId(UUID.randomUUID());
        p.setCompanyId(UUID.randomUUID());
        p.setCode("STANDARD");
        p.setName("Standard 40h");
        p.setStandardHoursPerDay(new BigDecimal("8.00"));
        p.setStandardHoursPerWeek(new BigDecimal("40.00"));
        p.setWorkingDays("MON,TUE,WED,THU,FRI");
        p.setGraceMinutes(5);
        p.setOvertimeMultiplier(new BigDecimal("1.50"));
        p.setPeriodLengthDays(7);
        p.setActive(true);

        AttendancePolicyResponse r = mapper.toResponse(p);
        assertThat(r.code()).isEqualTo("STANDARD");
        assertThat(r.workingDays()).isEqualTo("MON,TUE,WED,THU,FRI");
        assertThat(r.periodLengthDays()).isEqualTo(7);
        assertThat(r.active()).isTrue();
    }

    @Test
    void timeEntryMapsAllFields() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());

        TimeEntry entry = new TimeEntry();
        entry.setId(UUID.randomUUID());
        entry.setTenantId(UUID.randomUUID());
        entry.setCompanyId(UUID.randomUUID());
        entry.setEmployee(e);
        entry.setEventType(TimeEventType.IN);
        entry.setOccurredAt(Instant.parse("2026-01-05T09:00:00Z"));
        entry.setSource(TimeEntrySource.KIOSK);
        entry.setLocation("HQ");

        TimeEntryResponse r = mapper.toResponse(entry);
        assertThat(r.eventType()).isEqualTo(TimeEventType.IN);
        assertThat(r.source()).isEqualTo(TimeEntrySource.KIOSK);
        assertThat(r.employeeId()).isEqualTo(e.getId());
    }

    @Test
    void timesheetMapsAllFields() {
        Employee e = new Employee();
        e.setId(UUID.randomUUID());

        Timesheet ts = new Timesheet();
        ts.setId(UUID.randomUUID());
        ts.setTenantId(UUID.randomUUID());
        ts.setCompanyId(UUID.randomUUID());
        ts.setEmployee(e);
        ts.setPeriodStart(LocalDate.of(2026, 1, 5));
        ts.setPeriodEnd(LocalDate.of(2026, 1, 11));
        ts.setWorkedHours(new BigDecimal("40.00"));
        ts.setOvertimeHours(new BigDecimal("2.00"));
        ts.setStatus(TimesheetStatus.SUBMITTED);
        ts.setSubmittedAt(Instant.now());
        ts.setWorkflowInstanceId(UUID.randomUUID());

        TimesheetResponse r = mapper.toResponse(ts);
        assertThat(r.workedHours()).isEqualByComparingTo("40.00");
        assertThat(r.overtimeHours()).isEqualByComparingTo("2.00");
        assertThat(r.status()).isEqualTo(TimesheetStatus.SUBMITTED);
        assertThat(r.workflowInstanceId()).isNotNull();
    }
}
