package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.attendance.application.TimesheetService;
import com.ewos.employee.api.dto.MssDashboardResponse;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.MssAttendanceStatus;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.application.LeaveRequestService;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;

/** Sprint 27C — {@link MssDashboardService}: team summary, attendance snapshot, upcoming leave. */
@ExtendWith(MockitoExtension.class)
class MssDashboardServiceTest {

    @Mock EmployeeRepository employees;
    @Mock EmployeeContext employeeContext;
    @Mock TenantContext tenantContext;
    @Mock EffectiveManagerResolver effectiveManagerResolver;
    @Mock ManagerApprovalsService approvals;
    @Mock LeaveRequestService leaveRequestService;
    @Mock TimesheetService timesheetService;
    @Mock LeaveRequestRepository leaveRequests;

    private MssDashboardService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID managerEmployeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new MssDashboardService(
                        employees,
                        employeeContext,
                        tenantContext,
                        effectiveManagerResolver,
                        approvals,
                        leaveRequestService,
                        timesheetService,
                        leaveRequests);
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(managerEmployeeId));
        lenient()
                .when(
                        effectiveManagerResolver.resolve(
                                eq(tenantId),
                                eq(managerEmployeeId),
                                any(),
                                anyString(),
                                anyString()))
                .thenReturn(managerEmployeeId);
    }

    private static Employee report(UUID id, String displayName) {
        Employee e = new Employee();
        e.setId(id);
        e.setDisplayName(displayName);
        return e;
    }

    @Test
    void requiresALinkedEmployeeRecord() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.dashboard(null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(employees, never()).findAllByTenantIdAndManagerId(any(), any(), any());
    }

    @Test
    void headcountAndPendingCountsComeFromTheirRespectiveModules() {
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(managerEmployeeId), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(approvals.countPending(null)).thenReturn(7L);
        when(timesheetService.countPendingForManager(tenantId, managerEmployeeId)).thenReturn(3L);
        when(leaveRequestService.countPendingForManager(tenantId, managerEmployeeId))
                .thenReturn(2L);

        MssDashboardResponse response = service.dashboard(null);

        assertThat(response.teamSummary().headcount()).isZero();
        assertThat(response.teamSummary().pendingApprovals()).isEqualTo(7L);
        assertThat(response.teamSummary().timesheetsPending()).isEqualTo(3L);
        assertThat(response.teamSummary().leaveRequestsPending()).isEqualTo(2L);
    }

    @Test
    void classifiesDirectReportsOnApprovedLeaveTodayAsOnLeaveAndEveryoneElseAsNotMarked() {
        UUID onLeaveId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Employee onLeaveEmployee = report(onLeaveId, "On Leave Employee");
        Employee otherEmployee = report(otherId, "Other Employee");
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(managerEmployeeId), any()))
                .thenReturn(new PageImpl<>(List.of(onLeaveEmployee, otherEmployee)));
        LeaveRequest onLeaveRequest = new LeaveRequest();
        onLeaveRequest.setEmployee(onLeaveEmployee);
        LocalDate today = LocalDate.now();
        when(leaveRequests.findApprovedOverlappingForEmployees(
                        tenantId, List.of(onLeaveId, otherId), today, today))
                .thenReturn(List.of(onLeaveRequest));
        when(leaveRequests.findApprovedOverlappingForEmployees(
                        eq(tenantId),
                        eq(List.of(onLeaveId, otherId)),
                        eq(today),
                        org.mockito.ArgumentMatchers.argThat(d -> d != null && d.isAfter(today))))
                .thenReturn(List.of());
        when(approvals.countPending(null)).thenReturn(0L);
        when(timesheetService.countPendingForManager(tenantId, managerEmployeeId)).thenReturn(0L);
        when(leaveRequestService.countPendingForManager(tenantId, managerEmployeeId))
                .thenReturn(0L);

        MssDashboardResponse response = service.dashboard(null);

        assertThat(response.teamSummary().onLeaveToday()).isEqualTo(1L);
        assertThat(response.teamAttendanceSnapshot())
                .extracting(e -> e.employeeId(), e -> e.status())
                .containsExactlyInAnyOrder(
                        org.assertj.core.api.Assertions.tuple(
                                onLeaveId, MssAttendanceStatus.ON_LEAVE),
                        org.assertj.core.api.Assertions.tuple(
                                otherId, MssAttendanceStatus.NOT_MARKED));
    }

    @Test
    void upcomingTeamLeaveIsSortedByStartDateAndCoversTheNextFourteenDays() {
        UUID reportId = UUID.randomUUID();
        Employee reportEmployee = report(reportId, "Report");
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(managerEmployeeId), any()))
                .thenReturn(new PageImpl<>(List.of(reportEmployee)));
        LocalDate today = LocalDate.now();
        LeaveRequest later = new LeaveRequest();
        later.setEmployee(reportEmployee);
        later.setStartDate(today.plusDays(10));
        later.setEndDate(today.plusDays(12));
        LeaveType lt = new LeaveType();
        lt.setName("Annual Leave");
        later.setLeaveType(lt);
        LeaveRequest sooner = new LeaveRequest();
        sooner.setEmployee(reportEmployee);
        sooner.setStartDate(today.plusDays(2));
        sooner.setEndDate(today.plusDays(3));
        sooner.setLeaveType(lt);
        when(leaveRequests.findApprovedOverlappingForEmployees(
                        tenantId, List.of(reportId), today, today))
                .thenReturn(List.of());
        when(leaveRequests.findApprovedOverlappingForEmployees(
                        tenantId, List.of(reportId), today, today.plusDays(14)))
                .thenReturn(List.of(later, sooner));
        when(approvals.countPending(null)).thenReturn(0L);
        when(timesheetService.countPendingForManager(tenantId, managerEmployeeId)).thenReturn(0L);
        when(leaveRequestService.countPendingForManager(tenantId, managerEmployeeId))
                .thenReturn(0L);

        MssDashboardResponse response = service.dashboard(null);

        assertThat(response.upcomingTeamLeave())
                .extracting(e -> e.startDate())
                .containsExactly(today.plusDays(2), today.plusDays(10));
    }

    @Test
    void delegatesThroughEffectiveManagerResolverForActingFor() {
        UUID peerId = UUID.randomUUID();
        UUID peerManagerId = UUID.randomUUID();
        when(effectiveManagerResolver.resolve(
                        eq(tenantId), eq(managerEmployeeId), eq(peerId), anyString(), anyString()))
                .thenReturn(peerManagerId);
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(peerManagerId), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(approvals.countPending(peerId)).thenReturn(0L);
        when(timesheetService.countPendingForManager(tenantId, peerManagerId)).thenReturn(0L);
        when(leaveRequestService.countPendingForManager(tenantId, peerManagerId)).thenReturn(0L);

        service.dashboard(peerId);

        verify(employees).findAllByTenantIdAndManagerId(eq(tenantId), eq(peerManagerId), any());
        verify(approvals).countPending(peerId);
    }
}
