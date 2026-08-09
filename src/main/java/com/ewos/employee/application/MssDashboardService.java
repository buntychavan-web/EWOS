package com.ewos.employee.application;

import com.ewos.attendance.application.TimesheetService;
import com.ewos.employee.api.dto.MssDashboardResponse;
import com.ewos.employee.api.dto.MssTeamAttendanceEntry;
import com.ewos.employee.api.dto.MssTeamSummaryResponse;
import com.ewos.employee.api.dto.MssUpcomingLeaveEntry;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.MssAttendanceStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.application.LeaveRequestService;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27C — MSS Dashboard (PRD §4.2). A read-only aggregate over already-owned, already-tested
 * per-module reads, mirroring {@code EssDashboardService}'s pattern. Direct reports only — same
 * {@code manager.id} equality {@link MssTeamService} uses, no indirect-report hierarchy.
 */
@Service
public class MssDashboardService {

    /**
     * Bounds how many direct reports feed {@code teamAttendanceSnapshot}, mirroring {@code
     * ManagerApprovalsService.PER_MODULE_FETCH_CAP}: a dashboard card must stay bounded work
     * regardless of team size.
     */
    private static final int TEAM_SNAPSHOT_CAP = 200;

    private static final int UPCOMING_LEAVE_DAYS = 14;
    private static final String ACTING_FOR_ACTION = "MSS_DASHBOARD_ACTING_FOR";

    private final EmployeeRepository employees;
    private final EmployeeContext employeeContext;
    private final TenantContext tenantContext;
    private final EffectiveManagerResolver effectiveManagerResolver;
    private final ManagerApprovalsService approvals;
    private final LeaveRequestService leaveRequestService;
    private final TimesheetService timesheetService;
    private final LeaveRequestRepository leaveRequests;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public MssDashboardService(
            EmployeeRepository employees,
            EmployeeContext employeeContext,
            TenantContext tenantContext,
            EffectiveManagerResolver effectiveManagerResolver,
            ManagerApprovalsService approvals,
            LeaveRequestService leaveRequestService,
            TimesheetService timesheetService,
            LeaveRequestRepository leaveRequests) {
        this.employees = employees;
        this.employeeContext = employeeContext;
        this.tenantContext = tenantContext;
        this.effectiveManagerResolver = effectiveManagerResolver;
        this.approvals = approvals;
        this.leaveRequestService = leaveRequestService;
        this.timesheetService = timesheetService;
        this.leaveRequests = leaveRequests;
    }

    @Transactional(readOnly = true)
    public MssDashboardResponse dashboard(UUID actingForEmployeeId) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID callerEmployeeId = requireEmployeeId();
        UUID effectiveManagerId =
                effectiveManagerResolver.resolve(
                        tenantId,
                        callerEmployeeId,
                        actingForEmployeeId,
                        ACTING_FOR_ACTION,
                        "Team dashboard not found");

        Pageable window = PageRequest.of(0, TEAM_SNAPSHOT_CAP, Sort.by("displayName").ascending());
        List<Employee> directReports =
                employees
                        .findAllByTenantIdAndManagerId(tenantId, effectiveManagerId, window)
                        .getContent();
        List<UUID> directReportIds = directReports.stream().map(Employee::getId).toList();

        LocalDate today = LocalDate.now();
        Set<UUID> onLeaveTodayIds =
                directReportIds.isEmpty()
                        ? Set.of()
                        : leaveRequests
                                .findApprovedOverlappingForEmployees(
                                        tenantId, directReportIds, today, today)
                                .stream()
                                .map(r -> r.getEmployee().getId())
                                .collect(Collectors.toCollection(HashSet::new));

        long headcount = employees.countByTenantIdAndManagerId(tenantId, effectiveManagerId);
        long pendingApprovals = approvals.countPending(actingForEmployeeId);
        long timesheetsPending =
                timesheetService.countPendingForManager(tenantId, effectiveManagerId);
        long leaveRequestsPending =
                leaveRequestService.countPendingForManager(tenantId, effectiveManagerId);
        MssTeamSummaryResponse teamSummary =
                new MssTeamSummaryResponse(
                        headcount,
                        onLeaveTodayIds.size(),
                        pendingApprovals,
                        timesheetsPending,
                        leaveRequestsPending);

        List<MssTeamAttendanceEntry> attendanceSnapshot =
                directReports.stream()
                        .map(
                                e ->
                                        new MssTeamAttendanceEntry(
                                                e.getId(),
                                                displayNameOf(e),
                                                onLeaveTodayIds.contains(e.getId())
                                                        ? MssAttendanceStatus.ON_LEAVE
                                                        : MssAttendanceStatus.NOT_MARKED))
                        .toList();

        List<MssUpcomingLeaveEntry> upcomingTeamLeave =
                directReportIds.isEmpty()
                        ? List.of()
                        : upcomingLeave(tenantId, directReportIds, today);

        return new MssDashboardResponse(teamSummary, attendanceSnapshot, upcomingTeamLeave);
    }

    private List<MssUpcomingLeaveEntry> upcomingLeave(
            UUID tenantId, List<UUID> directReportIds, LocalDate today) {
        LocalDate horizon = today.plusDays(UPCOMING_LEAVE_DAYS);
        List<LeaveRequest> overlapping =
                leaveRequests.findApprovedOverlappingForEmployees(
                        tenantId, directReportIds, today, horizon);
        List<MssUpcomingLeaveEntry> entries = new ArrayList<>();
        for (LeaveRequest r : overlapping) {
            entries.add(
                    new MssUpcomingLeaveEntry(
                            r.getEmployee().getId(),
                            displayNameOf(r.getEmployee()),
                            r.getStartDate(),
                            r.getEndDate(),
                            r.getLeaveType() == null ? null : r.getLeaveType().getName()));
        }
        entries.sort(Comparator.comparing(MssUpcomingLeaveEntry::startDate));
        return entries;
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

    private static String displayNameOf(Employee e) {
        if (e.getDisplayName() != null && !e.getDisplayName().isBlank()) {
            return e.getDisplayName();
        }
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }
}
