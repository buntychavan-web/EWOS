package com.ewos.attendance.application;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.DecideTimesheetRequest;
import com.ewos.attendance.api.dto.OpenTimesheetRequest;
import com.ewos.attendance.api.dto.SubmitTimesheetRequest;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.domain.TimeEntry;
import com.ewos.attendance.domain.Timesheet;
import com.ewos.attendance.domain.TimesheetCalculator;
import com.ewos.attendance.domain.TimesheetCalculator.Totals;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.attendance.domain.events.AttendanceEvent;
import com.ewos.attendance.domain.events.AttendanceEventType;
import com.ewos.attendance.infrastructure.persistence.TimeEntryRepository;
import com.ewos.attendance.infrastructure.persistence.TimesheetRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TimesheetService {

    private static final String SUBJECT_TYPE = "attendance.timesheet";

    private final TimesheetRepository timesheets;
    private final TimeEntryRepository entries;
    private final EmployeeRepository employees;
    private final AttendancePolicyService policies;
    private final TimesheetCalculator calculator;
    private final WorkflowInstanceService workflow;
    private final AttendanceMapper mapper;
    private final org.springframework.context.ApplicationEventPublisher events;

    public TimesheetService(
            TimesheetRepository timesheets,
            TimeEntryRepository entries,
            EmployeeRepository employees,
            AttendancePolicyService policies,
            TimesheetCalculator calculator,
            WorkflowInstanceService workflow,
            AttendanceMapper mapper,
            org.springframework.context.ApplicationEventPublisher events) {
        this.timesheets = timesheets;
        this.entries = entries;
        this.employees = employees;
        this.policies = policies;
        this.calculator = calculator;
        this.workflow = workflow;
        this.mapper = mapper;
        this.events = events;
    }

    /** Opens (or returns the existing) DRAFT timesheet for the given employee-period. */
    public TimesheetResponse open(OpenTimesheetRequest request) {
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "periodEnd must be on or after periodStart");
        }
        return timesheets
                .findByEmployeeAndPeriod(
                        request.tenantId(),
                        request.employeeId(),
                        request.periodStart(),
                        request.periodEnd())
                .map(mapper::toResponse)
                .orElseGet(() -> createDraft(request));
    }

    /** Recomputes rollups from raw entries; safe to call while DRAFT. */
    public TimesheetResponse recompute(UUID tenantId, UUID id) {
        Timesheet ts = require(tenantId, id);
        if (ts.getStatus() != TimesheetStatus.DRAFT) {
            throw new ApiException(HttpStatus.CONFLICT, "Only DRAFT timesheets can be recomputed");
        }
        List<TimeEntry> raw =
                entries.findForEmployeeInRange(
                        tenantId,
                        ts.getEmployee().getId(),
                        ts.getPeriodStart().atStartOfDay().toInstant(ZoneOffset.UTC),
                        ts.getPeriodEnd().atTime(LocalTime.MAX).toInstant(ZoneOffset.UTC));
        Totals totals =
                calculator.calculate(ts.getPolicy(), ts.getPeriodStart(), ts.getPeriodEnd(), raw);
        ts.setWorkedHours(totals.workedHours());
        ts.setOvertimeHours(totals.overtimeHours());
        ts.setBreakHours(totals.breakHours());
        ts.setAbsenceHours(totals.absenceHours());
        return mapper.toResponse(ts);
    }

    public TimesheetResponse submit(UUID tenantId, UUID id, SubmitTimesheetRequest request) {
        Timesheet ts = require(tenantId, id);
        if (ts.getStatus() != TimesheetStatus.DRAFT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Only DRAFT timesheets can be submitted (current status: "
                            + ts.getStatus()
                            + ")");
        }
        // Recompute one last time so submitted totals reflect all captured entries.
        recompute(tenantId, id);

        WorkflowInstanceResponse instance =
                workflow.start(
                        new StartInstanceRequest(
                                tenantId,
                                ts.getCompanyId(),
                                request.workflowDefinitionId(),
                                SUBJECT_TYPE,
                                ts.getId(),
                                SUBJECT_TYPE + ":" + ts.getId()));

        ts.setStatus(TimesheetStatus.SUBMITTED);
        ts.setSubmittedAt(Instant.now());
        ts.setWorkflowInstanceId(instance.id());

        publish(AttendanceEventType.TIMESHEET_SUBMITTED, ts);
        return mapper.toResponse(ts);
    }

    public TimesheetResponse approve(UUID tenantId, UUID id, DecideTimesheetRequest request) {
        Timesheet ts = require(tenantId, id);
        if (ts.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Only SUBMITTED timesheets can be approved");
        }
        UUID actor = requireActor();
        ts.setStatus(TimesheetStatus.APPROVED);
        ts.setApprovedAt(Instant.now());
        ts.setApprovedBy(actor);
        publish(AttendanceEventType.TIMESHEET_APPROVED, ts);
        return mapper.toResponse(ts);
    }

    public TimesheetResponse reject(UUID tenantId, UUID id, DecideTimesheetRequest request) {
        Timesheet ts = require(tenantId, id);
        if (ts.getStatus() != TimesheetStatus.SUBMITTED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Only SUBMITTED timesheets can be rejected");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        UUID actor = requireActor();
        ts.setStatus(TimesheetStatus.REJECTED);
        ts.setRejectedAt(Instant.now());
        ts.setRejectedBy(actor);
        ts.setRejectionReason(request.reason());
        publish(AttendanceEventType.TIMESHEET_REJECTED, ts);
        return mapper.toResponse(ts);
    }

    public TimesheetResponse cancel(UUID tenantId, UUID id) {
        Timesheet ts = require(tenantId, id);
        if (ts.getStatus() == TimesheetStatus.APPROVED
                || ts.getStatus() == TimesheetStatus.CANCELLED) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Cannot cancel a timesheet in status " + ts.getStatus());
        }
        ts.setStatus(TimesheetStatus.CANCELLED);
        publish(AttendanceEventType.TIMESHEET_CANCELLED, ts);
        return mapper.toResponse(ts);
    }

    @Transactional(readOnly = true)
    public TimesheetResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<TimesheetResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return timesheets.findAllForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TimesheetResponse> byStatus(UUID tenantId, TimesheetStatus status) {
        return timesheets
                .findAllByTenantIdAndStatusOrderByPeriodStartDesc(tenantId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private TimesheetResponse createDraft(OpenTimesheetRequest request) {
        Employee employee =
                employees
                        .findByIdAndTenantId(request.employeeId(), request.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Employee belongs to a different company than the timesheet claims");
        }
        AttendancePolicy policy =
                request.policyId() != null
                        ? policies.require(request.tenantId(), request.policyId())
                        : policies.effectivePolicyFor(request.tenantId(), request.companyId());

        Timesheet ts = new Timesheet();
        ts.setTenantId(request.tenantId());
        ts.setCompanyId(request.companyId());
        ts.setEmployee(employee);
        ts.setPolicy(policy);
        ts.setPeriodStart(request.periodStart());
        ts.setPeriodEnd(request.periodEnd());
        ts.setStatus(TimesheetStatus.DRAFT);
        Timesheet saved = timesheets.save(ts);
        publish(AttendanceEventType.TIMESHEET_CREATED, saved);
        return mapper.toResponse(saved);
    }

    private Timesheet require(UUID tenantId, UUID id) {
        return timesheets
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Timesheet not found"));
    }

    private void publish(AttendanceEventType type, Timesheet ts) {
        events.publishEvent(
                new AttendanceEvent(
                        type,
                        ts.getTenantId(),
                        ts.getCompanyId(),
                        ts.getEmployee() != null ? ts.getEmployee().getId() : null,
                        null,
                        ts.getId(),
                        ts.getPeriodStart(),
                        ts.getPeriodEnd(),
                        ts.getWorkflowInstanceId(),
                        currentActor(),
                        Instant.now()));
    }

    private static UUID requireActor() {
        UUID actor = currentActor();
        if (actor == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
        }
        return actor;
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
