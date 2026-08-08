package com.ewos.employee.application;

import com.ewos.attendance.api.dto.DecideTimesheetRequest;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.attendance.application.TimesheetService;
import com.ewos.employee.api.dto.ApprovalItemResponse;
import com.ewos.employee.api.dto.ApprovalsPageResponse;
import com.ewos.employee.api.dto.BulkApprovalActionRequest;
import com.ewos.employee.api.dto.BulkApprovalActionResponse;
import com.ewos.employee.api.dto.BulkApprovalItemRequest;
import com.ewos.employee.api.dto.BulkApprovalItemResult;
import com.ewos.employee.domain.ApprovalAction;
import com.ewos.employee.domain.ApprovalSourceModule;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.dto.DecideLeaveRequestRequest;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.application.LeaveRequestService;
import com.ewos.performance.api.dto.AppraisalResponse;
import com.ewos.performance.application.AppraisalService;
import com.ewos.probation.api.dto.ProbationRecordResponse;
import com.ewos.probation.application.ProbationService;
import com.ewos.recruitment.api.dto.JobRequisitionResponse;
import com.ewos.recruitment.application.JobRequisitionService;
import com.ewos.shared.audit.CrossEmployeeAccessLogService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.workflow.application.WorkflowDelegationService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27B — the unified "My Approvals" inbox (PRD §27). Aggregates each module's own,
 * already-tested {@code pendingForManager} query and {@code approve}/{@code reject} methods rather
 * than wrapping {@code WorkflowTaskController.myTasks()} as the PRD's literal text assumes:
 * repository investigation before implementation confirmed none of Leave/Timesheet/Performance/
 * Probation/Requisition ever create a {@code WorkflowTask} row (each attaches only an audit-trail
 * {@code WorkflowInstance}, a deliberate, documented Sprint 21/24 design choice — see
 * V43/V44/V48__*_approval_workflow.sql), so {@code myTasks()} would return nothing for any of them.
 * See the PR description for the full rationale and the two rounds of user sign-off behind this
 * deviation.
 *
 * <p>Only {@link ApprovalSourceModule#LEAVE} and {@link ApprovalSourceModule#TIMESHEET} are
 * act-through: both already enforce a genuine manager-of-employee relationship in their own {@code
 * approve}/{@code reject} ({@code requireManagerAuthorityUnlessAdmin}, now delegation-aware — see
 * those classes). Performance's manager step requires a numeric rating (not a bare approve/reject),
 * Probation's {@code PROBATION_APPROVE} is a flat platform permission with no manager-relationship
 * check in the service layer, and Requisition's hiring manager is a direct field, not an employee's
 * manager chain — none of the three fit this inbox's generic approve/reject action safely without
 * inventing new authorization logic the PRD explicitly says not to. They surface as read-only
 * summary cards with a deep link to their own existing screen instead.
 */
@Service
public class ManagerApprovalsService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int PER_MODULE_FETCH_CAP = 200;

    private static final Comparator<ApprovalItemResponse> ORDER =
            Comparator.comparing(ApprovalItemResponse::pendingSince)
                    .thenComparing(i -> i.sourceModule().name())
                    .thenComparing(i -> i.sourceId().toString());

    private final LeaveRequestService leave;
    private final TimesheetService timesheets;
    private final AppraisalService performance;
    private final ProbationService probation;
    private final JobRequisitionService requisitions;
    private final EmployeeRepository employees;
    private final EmployeeContext employeeContext;
    private final TenantContext tenantContext;
    private final WorkflowDelegationService delegations;
    private final CrossEmployeeAccessLogService accessLog;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public ManagerApprovalsService(
            LeaveRequestService leave,
            TimesheetService timesheets,
            AppraisalService performance,
            ProbationService probation,
            JobRequisitionService requisitions,
            EmployeeRepository employees,
            EmployeeContext employeeContext,
            TenantContext tenantContext,
            WorkflowDelegationService delegations,
            CrossEmployeeAccessLogService accessLog) {
        this.leave = leave;
        this.timesheets = timesheets;
        this.performance = performance;
        this.probation = probation;
        this.requisitions = requisitions;
        this.employees = employees;
        this.employeeContext = employeeContext;
        this.tenantContext = tenantContext;
        this.delegations = delegations;
        this.accessLog = accessLog;
    }

    @Transactional(readOnly = true)
    public ApprovalsPageResponse list(UUID actingForEmployeeId, String cursor, Integer limit) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID callerEmployeeId = requireEmployeeId();
        boolean delegated =
                actingForEmployeeId != null && !actingForEmployeeId.equals(callerEmployeeId);
        UUID effectiveManagerId =
                resolveEffectiveManagerEmployeeId(tenantId, callerEmployeeId, actingForEmployeeId);

        int pageSize =
                (limit == null || limit <= 0) ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
        Pageable window = PageRequest.of(0, PER_MODULE_FETCH_CAP);

        List<LeaveRequestResponse> leaveItems =
                leave.pendingForManager(tenantId, effectiveManagerId, window).getContent();
        List<TimesheetResponse> timesheetItems =
                timesheets.pendingForManager(tenantId, effectiveManagerId, window).getContent();
        List<AppraisalResponse> performanceItems =
                performance.pendingForManager(tenantId, effectiveManagerId);
        List<ProbationRecordResponse> probationItems =
                probation.pendingForManager(tenantId, effectiveManagerId, window).getContent();
        List<JobRequisitionResponse> requisitionItems =
                requisitions.pendingForManager(tenantId, effectiveManagerId, window).getContent();

        Set<UUID> subjectIds = new HashSet<>();
        leaveItems.forEach(r -> subjectIds.add(r.employeeId()));
        timesheetItems.forEach(r -> subjectIds.add(r.employeeId()));
        performanceItems.forEach(r -> subjectIds.add(r.employeeId()));
        probationItems.forEach(r -> subjectIds.add(r.employeeId()));
        Map<UUID, String> names = nameLookup(tenantId, subjectIds);

        List<ApprovalItemResponse> merged = new ArrayList<>();
        leaveItems.forEach(r -> merged.add(fromLeave(r, names)));
        timesheetItems.forEach(r -> merged.add(fromTimesheet(r, names)));
        performanceItems.forEach(r -> merged.add(fromPerformance(r, names)));
        probationItems.forEach(r -> merged.add(fromProbation(r, names)));
        requisitionItems.forEach(r -> merged.add(fromRequisition(r)));
        merged.sort(ORDER);

        int startIndex = cursor == null || cursor.isBlank() ? 0 : indexAfterCursor(merged, cursor);
        int endIndex = Math.min(startIndex + pageSize, merged.size());
        List<ApprovalItemResponse> page =
                startIndex >= merged.size() ? List.of() : merged.subList(startIndex, endIndex);
        String nextCursor =
                endIndex < merged.size() ? encodeCursor(merged.get(endIndex - 1)) : null;

        return new ApprovalsPageResponse(page, nextCursor, delegated);
    }

    public ApprovalItemResponse decide(
            ApprovalSourceModule sourceModule,
            UUID sourceId,
            UUID actingForEmployeeId,
            ApprovalAction action,
            String notes) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID callerEmployeeId = requireEmployeeId();
        resolveEffectiveManagerEmployeeId(tenantId, callerEmployeeId, actingForEmployeeId);
        return dispatch(tenantId, sourceModule, sourceId, action, notes);
    }

    /**
     * Each item is dispatched with no enclosing transaction at this level, so every call to {@code
     * leave.approve}/{@code timesheet.approve} below runs in its own independent transaction (each
     * of those service beans is itself {@code @Transactional}) — a failure on one item can never
     * roll back another item's already-committed decision (PRD §5.4/§17).
     */
    public BulkApprovalActionResponse bulkAct(
            BulkApprovalActionRequest request, UUID actingForEmployeeId) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID callerEmployeeId = requireEmployeeId();
        resolveEffectiveManagerEmployeeId(tenantId, callerEmployeeId, actingForEmployeeId);

        List<BulkApprovalItemResult> results = new ArrayList<>(request.items().size());
        int succeeded = 0;
        for (BulkApprovalItemRequest item : request.items()) {
            try {
                dispatch(
                        tenantId,
                        item.sourceModule(),
                        item.sourceId(),
                        item.action(),
                        item.notes());
                results.add(BulkApprovalItemResult.success(item.sourceModule(), item.sourceId()));
                succeeded++;
            } catch (ApiException e) {
                results.add(
                        BulkApprovalItemResult.failed(
                                item.sourceModule(), item.sourceId(), safeMessage(e)));
            } catch (RuntimeException e) {
                results.add(
                        BulkApprovalItemResult.failed(
                                item.sourceModule(), item.sourceId(), "Unexpected error"));
            }
        }
        return new BulkApprovalActionResponse(results, succeeded, results.size() - succeeded);
    }

    private ApprovalItemResponse dispatch(
            UUID tenantId,
            ApprovalSourceModule sourceModule,
            UUID sourceId,
            ApprovalAction action,
            String notes) {
        return switch (sourceModule) {
            case LEAVE -> {
                LeaveRequestResponse r =
                        action == ApprovalAction.APPROVE
                                ? leave.approve(
                                        tenantId, sourceId, new DecideLeaveRequestRequest(notes))
                                : leave.reject(
                                        tenantId, sourceId, new DecideLeaveRequestRequest(notes));
                yield fromLeave(r, nameLookup(tenantId, Set.of(r.employeeId())));
            }
            case TIMESHEET -> {
                TimesheetResponse r =
                        action == ApprovalAction.APPROVE
                                ? timesheets.approve(
                                        tenantId, sourceId, new DecideTimesheetRequest(notes))
                                : timesheets.reject(
                                        tenantId, sourceId, new DecideTimesheetRequest(notes));
                yield fromTimesheet(r, nameLookup(tenantId, Set.of(r.employeeId())));
            }
            case PERFORMANCE, PROBATION, REQUISITION ->
                    throw new ApiException(
                            HttpStatus.BAD_REQUEST,
                            "This approval type is read-only in the unified inbox — act on it from"
                                    + " its own module screen");
        };
    }

    /**
     * Uniform 404 whether {@code actingForEmployeeId} doesn't exist, belongs to another tenant, or
     * is simply not an active delegator to the caller — enumeration protection per PRD §17. Logs
     * every delegated access (granted or denied) to the cross-employee access log.
     */
    private UUID resolveEffectiveManagerEmployeeId(
            UUID tenantId, UUID callerEmployeeId, UUID actingForEmployeeId) {
        if (actingForEmployeeId == null || actingForEmployeeId.equals(callerEmployeeId)) {
            return callerEmployeeId;
        }
        Employee delegator =
                employees.findByIdAndTenantId(actingForEmployeeId, tenantId).orElse(null);
        UUID callerUserId = tenantContext.currentUserId().orElse(null);
        boolean active =
                delegator != null
                        && delegator.getUserId() != null
                        && callerUserId != null
                        && delegations.isActiveDelegateOf(
                                tenantId, delegator.getUserId(), callerUserId);
        if (!active) {
            accessLog.logDenied(
                    tenantId,
                    callerEmployeeId,
                    actingForEmployeeId,
                    "MSS_APPROVALS_ACTING_FOR",
                    "no active delegation");
            throw new ApiException(HttpStatus.NOT_FOUND, "Approvals inbox not found");
        }
        accessLog.logGranted(
                tenantId, callerEmployeeId, actingForEmployeeId, "MSS_APPROVALS_ACTING_FOR");
        return actingForEmployeeId;
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

    private Map<UUID, String> nameLookup(UUID tenantId, Set<UUID> ids) {
        Set<UUID> nonNull = new HashSet<>(ids);
        nonNull.remove(null);
        if (nonNull.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        employees
                .findAllByIdInAndTenantId(nonNull, tenantId)
                .forEach(e -> names.put(e.getId(), displayNameOf(e)));
        return names;
    }

    private static String displayNameOf(Employee e) {
        if (e.getDisplayName() != null && !e.getDisplayName().isBlank()) {
            return e.getDisplayName();
        }
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    private static ApprovalItemResponse fromLeave(LeaveRequestResponse r, Map<UUID, String> names) {
        return new ApprovalItemResponse(
                ApprovalSourceModule.LEAVE,
                r.id(),
                true,
                r.employeeId(),
                names.get(r.employeeId()),
                r.status().name(),
                "Leave request: "
                        + r.daysRequested()
                        + " day(s), "
                        + r.startDate()
                        + " to "
                        + r.endDate(),
                r.submittedAt(),
                "/leave/requests/" + r.id());
    }

    private static ApprovalItemResponse fromTimesheet(
            TimesheetResponse r, Map<UUID, String> names) {
        return new ApprovalItemResponse(
                ApprovalSourceModule.TIMESHEET,
                r.id(),
                true,
                r.employeeId(),
                names.get(r.employeeId()),
                r.status().name(),
                "Timesheet: " + r.periodStart() + " to " + r.periodEnd(),
                r.submittedAt(),
                "/attendance/timesheets/" + r.id());
    }

    private static ApprovalItemResponse fromPerformance(
            AppraisalResponse r, Map<UUID, String> names) {
        return new ApprovalItemResponse(
                ApprovalSourceModule.PERFORMANCE,
                r.id(),
                false,
                r.employeeId(),
                names.get(r.employeeId()),
                r.status().name(),
                "Appraisal awaiting your manager assessment",
                r.selfSubmittedAt(),
                "/performance/appraisals/" + r.id());
    }

    private static ApprovalItemResponse fromProbation(
            ProbationRecordResponse r, Map<UUID, String> names) {
        return new ApprovalItemResponse(
                ApprovalSourceModule.PROBATION,
                r.id(),
                false,
                r.employeeId(),
                names.get(r.employeeId()),
                r.status().name(),
                "Probation confirmation pending approval",
                r.updatedAt(),
                "/probation/records/" + r.id());
    }

    private static ApprovalItemResponse fromRequisition(JobRequisitionResponse r) {
        return new ApprovalItemResponse(
                ApprovalSourceModule.REQUISITION,
                r.id(),
                false,
                null,
                null,
                r.status().name(),
                "Requisition " + r.requisitionNumber() + ": " + r.title(),
                r.submittedAt(),
                "/recruitment/requisitions/" + r.id());
    }

    private static String safeMessage(ApiException e) {
        return e.getStatus() == HttpStatus.NOT_FOUND || e.getStatus() == HttpStatus.FORBIDDEN
                ? "Not found or not authorized"
                : e.getMessage();
    }

    private static int indexAfterCursor(List<ApprovalItemResponse> sorted, String cursor) {
        CursorKey key = decodeCursor(cursor);
        for (int i = 0; i < sorted.size(); i++) {
            if (compareToKey(sorted.get(i), key) > 0) {
                return i;
            }
        }
        return sorted.size();
    }

    private static int compareToKey(ApprovalItemResponse item, CursorKey key) {
        int c = item.pendingSince().compareTo(key.pendingSince());
        if (c != 0) {
            return c;
        }
        c = item.sourceModule().name().compareTo(key.sourceModule());
        if (c != 0) {
            return c;
        }
        return item.sourceId().toString().compareTo(key.sourceId());
    }

    private static String encodeCursor(ApprovalItemResponse item) {
        String raw = item.pendingSince() + "|" + item.sourceModule().name() + "|" + item.sourceId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static CursorKey decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 3);
            return new CursorKey(Instant.parse(parts[0]), parts[1], parts[2]);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid cursor", e);
        }
    }

    private record CursorKey(Instant pendingSince, String sourceModule, String sourceId) {}
}
