package com.ewos.leave.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.LeaveMapper;
import com.ewos.leave.api.dto.CreateLeaveRequestRequest;
import com.ewos.leave.api.dto.DecideLeaveRequestRequest;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.api.dto.SubmitLeaveRequestRequest;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveBalanceCalculator;
import com.ewos.leave.domain.LeavePolicy;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.domain.events.LeaveEvent;
import com.ewos.leave.domain.events.LeaveEventType;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.StartInstanceRequest;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class LeaveRequestService {

    private static final String SUBJECT_TYPE = "leave.request";

    private final LeaveRequestRepository requests;
    private final EmployeeRepository employees;
    private final LeaveTypeService leaveTypes;
    private final LeaveBalanceService balances;
    private final LeaveBalanceCalculator calculator;
    private final LeavePolicy policy;
    private final WorkflowInstanceService workflow;
    private final LeaveMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public LeaveRequestService(
            LeaveRequestRepository requests,
            EmployeeRepository employees,
            LeaveTypeService leaveTypes,
            LeaveBalanceService balances,
            LeaveBalanceCalculator calculator,
            LeavePolicy policy,
            WorkflowInstanceService workflow,
            LeaveMapper mapper,
            ApplicationEventPublisher events) {
        this(
                requests,
                employees,
                leaveTypes,
                balances,
                calculator,
                policy,
                workflow,
                mapper,
                events,
                Clock.systemUTC());
    }

    /** Test-friendly overload; production callers use the {@code Clock.systemUTC()} default. */
    LeaveRequestService(
            LeaveRequestRepository requests,
            EmployeeRepository employees,
            LeaveTypeService leaveTypes,
            LeaveBalanceService balances,
            LeaveBalanceCalculator calculator,
            LeavePolicy policy,
            WorkflowInstanceService workflow,
            LeaveMapper mapper,
            ApplicationEventPublisher events,
            Clock clock) {
        this.requests = requests;
        this.employees = employees;
        this.leaveTypes = leaveTypes;
        this.balances = balances;
        this.calculator = calculator;
        this.policy = policy;
        this.workflow = workflow;
        this.mapper = mapper;
        this.events = events;
        this.clock = clock;
    }

    public LeaveRequestResponse create(CreateLeaveRequestRequest request) {
        Employee employee = requireEmployee(request.tenantId(), request.employeeId());
        if (!employee.getCompanyId().equals(request.companyId())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Employee belongs to a different company");
        }
        LeaveType type = leaveTypes.require(request.tenantId(), request.leaveTypeId());
        BigDecimal days = calculator.countLeaveDays(request.startDate(), request.endDate());
        if (days.signum() <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Date range contains no working days; nothing to request");
        }

        LeaveRequest r = new LeaveRequest();
        r.setTenantId(request.tenantId());
        r.setCompanyId(request.companyId());
        r.setEmployee(employee);
        r.setLeaveType(type);
        r.setStartDate(request.startDate());
        r.setEndDate(request.endDate());
        r.setDaysRequested(days);
        r.setReason(request.reason());
        r.setStatus(LeaveRequestStatus.DRAFT);

        LeaveRequest saved = requests.save(r);
        publish(LeaveEventType.REQUEST_CREATED, saved, null);
        return mapper.toResponse(saved);
    }

    public LeaveRequestResponse submit(UUID tenantId, UUID id, SubmitLeaveRequestRequest request) {
        LeaveRequest r = require(tenantId, id);
        policy.assertSubmittable(r);
        policy.assertRequestable(r, LocalDate.now(clock));

        LeaveBalance balance =
                balances.requireBalanceForType(
                        tenantId,
                        r.getEmployee().getId(),
                        r.getLeaveType().getId(),
                        r.getStartDate().getYear(),
                        r.getCompanyId());
        BigDecimal available = calculator.availableDays(balance);
        policy.assertSufficientBalance(r, available);

        WorkflowInstanceResponse instance =
                workflow.start(
                        new StartInstanceRequest(
                                tenantId,
                                r.getCompanyId(),
                                request.workflowDefinitionId(),
                                SUBJECT_TYPE,
                                r.getId(),
                                SUBJECT_TYPE + ":" + r.getId()));

        // Move the requested days into the pending bucket to prevent double-booking.
        balance.setPendingDays(balance.getPendingDays().add(r.getDaysRequested()));

        r.setStatus(LeaveRequestStatus.SUBMITTED);
        r.setSubmittedAt(Instant.now());
        r.setWorkflowInstanceId(instance.id());

        publish(LeaveEventType.REQUEST_SUBMITTED, r, instance.id());
        return mapper.toResponse(r);
    }

    public LeaveRequestResponse approve(UUID tenantId, UUID id, DecideLeaveRequestRequest request) {
        LeaveRequest r = require(tenantId, id);
        policy.assertDecidable(r);
        UUID actor = requireActor();

        LeaveBalance balance =
                balances.requireBalanceForType(
                        tenantId,
                        r.getEmployee().getId(),
                        r.getLeaveType().getId(),
                        r.getStartDate().getYear(),
                        r.getCompanyId());
        // Move days from pending → consumed.
        balance.setPendingDays(
                balance.getPendingDays().subtract(r.getDaysRequested()).max(BigDecimal.ZERO));
        balance.setConsumedDays(balance.getConsumedDays().add(r.getDaysRequested()));

        r.setStatus(LeaveRequestStatus.APPROVED);
        r.setApprovedAt(Instant.now());
        r.setApprovedBy(actor);

        publish(LeaveEventType.REQUEST_APPROVED, r, r.getWorkflowInstanceId());
        return mapper.toResponse(r);
    }

    public LeaveRequestResponse reject(UUID tenantId, UUID id, DecideLeaveRequestRequest request) {
        LeaveRequest r = require(tenantId, id);
        policy.assertDecidable(r);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Rejection reason is required");
        }
        UUID actor = requireActor();

        LeaveBalance balance =
                balances.requireBalanceForType(
                        tenantId,
                        r.getEmployee().getId(),
                        r.getLeaveType().getId(),
                        r.getStartDate().getYear(),
                        r.getCompanyId());
        // Release the pending days back to available.
        balance.setPendingDays(
                balance.getPendingDays().subtract(r.getDaysRequested()).max(BigDecimal.ZERO));

        r.setStatus(LeaveRequestStatus.REJECTED);
        r.setRejectedAt(Instant.now());
        r.setRejectedBy(actor);
        r.setRejectionReason(request.reason());

        publish(LeaveEventType.REQUEST_REJECTED, r, r.getWorkflowInstanceId());
        return mapper.toResponse(r);
    }

    public LeaveRequestResponse cancel(UUID tenantId, UUID id, boolean actorIsAdmin) {
        LeaveRequest r = require(tenantId, id);
        policy.assertCancelable(r, actorIsAdmin);
        UUID actor = requireActor();

        // Return days from the appropriate bucket depending on state.
        if (r.getStatus() == LeaveRequestStatus.SUBMITTED
                || r.getStatus() == LeaveRequestStatus.APPROVED) {
            LeaveBalance balance =
                    balances.requireBalanceForType(
                            tenantId,
                            r.getEmployee().getId(),
                            r.getLeaveType().getId(),
                            r.getStartDate().getYear(),
                            r.getCompanyId());
            if (r.getStatus() == LeaveRequestStatus.SUBMITTED) {
                balance.setPendingDays(
                        balance.getPendingDays()
                                .subtract(r.getDaysRequested())
                                .max(BigDecimal.ZERO));
            } else {
                balance.setConsumedDays(
                        balance.getConsumedDays()
                                .subtract(r.getDaysRequested())
                                .max(BigDecimal.ZERO));
            }
        }

        r.setStatus(LeaveRequestStatus.CANCELLED);
        r.setCancelledAt(Instant.now());
        r.setCancelledBy(actor);

        publish(LeaveEventType.REQUEST_CANCELLED, r, r.getWorkflowInstanceId());
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return requests.findAllForEmployee(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestResponse> byStatus(UUID tenantId, LeaveRequestStatus status) {
        return requests.findAllByTenantIdAndStatusOrderByStartDateDesc(tenantId, status).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private LeaveRequest require(UUID tenantId, UUID id) {
        return requests.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found"));
    }

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private void publish(LeaveEventType type, LeaveRequest r, UUID workflowInstanceId) {
        events.publishEvent(
                new LeaveEvent(
                        type,
                        r.getTenantId(),
                        r.getCompanyId(),
                        r.getEmployee() != null ? r.getEmployee().getId() : null,
                        r.getLeaveType() != null ? r.getLeaveType().getId() : null,
                        r.getId(),
                        r.getStartDate(),
                        r.getEndDate(),
                        r.getDaysRequested(),
                        workflowInstanceId,
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
