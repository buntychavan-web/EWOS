package com.ewos.attendance.api;

import com.ewos.attendance.api.dto.AttendancePolicyResponse;
import com.ewos.attendance.api.dto.TimeEntryResponse;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.domain.Timesheet;
import org.springframework.stereotype.Component;

@Component
public final class AttendanceMapper {

    public AttendancePolicyResponse toResponse(AttendancePolicy p) {
        return new AttendancePolicyResponse(
                p.getId(),
                p.getTenantId(),
                p.getCompanyId(),
                p.getCode(),
                p.getName(),
                p.getDescription(),
                p.getStandardHoursPerDay(),
                p.getStandardHoursPerWeek(),
                p.getWorkingDays(),
                p.getGraceMinutes(),
                p.getOvertimeMultiplier(),
                p.getPeriodLengthDays(),
                p.isActive(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getCreatedBy(),
                p.getUpdatedBy(),
                p.getVersionNo());
    }

    public TimeEntryResponse toResponse(TimeEntry e) {
        return new TimeEntryResponse(
                e.getId(),
                e.getTenantId(),
                e.getCompanyId(),
                e.getEmployee() != null ? e.getEmployee().getId() : null,
                e.getEventType(),
                e.getOccurredAt(),
                e.getSource(),
                e.getLocation(),
                e.getNotes(),
                e.getCorrectionOf() != null ? e.getCorrectionOf().getId() : null,
                e.getCreatedAt(),
                e.getCreatedBy(),
                e.getVersionNo());
    }

    public TimesheetResponse toResponse(Timesheet t) {
        return new TimesheetResponse(
                t.getId(),
                t.getTenantId(),
                t.getCompanyId(),
                t.getEmployee() != null ? t.getEmployee().getId() : null,
                t.getPolicy() != null ? t.getPolicy().getId() : null,
                t.getPeriodStart(),
                t.getPeriodEnd(),
                t.getWorkedHours(),
                t.getOvertimeHours(),
                t.getBreakHours(),
                t.getAbsenceHours(),
                t.getStatus(),
                t.getSubmittedAt(),
                t.getApprovedAt(),
                t.getApprovedBy(),
                t.getRejectedAt(),
                t.getRejectedBy(),
                t.getRejectionReason(),
                t.getWorkflowInstanceId(),
                t.getCreatedAt(),
                t.getUpdatedAt(),
                t.getCreatedBy(),
                t.getUpdatedBy(),
                t.getVersionNo());
    }
}
