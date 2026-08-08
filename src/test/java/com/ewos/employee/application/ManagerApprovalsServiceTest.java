package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.attendance.api.dto.DecideTimesheetRequest;
import com.ewos.attendance.api.dto.TimesheetResponse;
import com.ewos.attendance.application.TimesheetService;
import com.ewos.attendance.domain.TimesheetStatus;
import com.ewos.employee.api.dto.ApprovalsPageResponse;
import com.ewos.employee.api.dto.BulkApprovalActionRequest;
import com.ewos.employee.api.dto.BulkApprovalActionResponse;
import com.ewos.employee.api.dto.BulkApprovalItemRequest;
import com.ewos.employee.domain.ApprovalAction;
import com.ewos.employee.domain.ApprovalSourceModule;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.dto.DecideLeaveRequestRequest;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.application.LeaveRequestService;
import com.ewos.leave.domain.LeaveRequestStatus;
import com.ewos.performance.application.AppraisalService;
import com.ewos.probation.application.ProbationService;
import com.ewos.recruitment.application.JobRequisitionService;
import com.ewos.shared.audit.CrossEmployeeAccessLogService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.workflow.application.WorkflowDelegationService;
import java.time.Instant;
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

/**
 * Sprint 27B — unified approvals inbox aggregator. Covers: own-inbox scoping, cross-module
 * merge/order, enumeration-safe delegation gating (granted and denied), act-through dispatch for
 * Leave/Timesheet, read-only rejection for Performance/Probation/Requisition, and bulk-act's
 * independent per-item success/failure reporting.
 */
@ExtendWith(MockitoExtension.class)
class ManagerApprovalsServiceTest {

    @Mock LeaveRequestService leave;
    @Mock TimesheetService timesheets;
    @Mock AppraisalService performance;
    @Mock ProbationService probation;
    @Mock JobRequisitionService requisitions;
    @Mock EmployeeRepository employees;
    @Mock EmployeeContext employeeContext;
    @Mock TenantContext tenantContext;
    @Mock WorkflowDelegationService delegations;
    @Mock CrossEmployeeAccessLogService accessLog;

    private ManagerApprovalsService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID callerEmployeeId = UUID.randomUUID();
    private final UUID callerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new ManagerApprovalsService(
                        leave,
                        timesheets,
                        performance,
                        probation,
                        requisitions,
                        employees,
                        employeeContext,
                        tenantContext,
                        delegations,
                        accessLog);
        lenient().when(tenantContext.homeTenantId()).thenReturn(tenantId);
        lenient()
                .when(employeeContext.currentEmployeeId())
                .thenReturn(Optional.of(callerEmployeeId));
        lenient().when(tenantContext.currentUserId()).thenReturn(Optional.of(callerUserId));
        lenient()
                .when(leave.pendingForManager(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        lenient()
                .when(timesheets.pendingForManager(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        lenient().when(performance.pendingForManager(any(), any())).thenReturn(List.of());
        lenient()
                .when(probation.pendingForManager(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        lenient()
                .when(requisitions.pendingForManager(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void listQueriesEachModuleScopedToTheCallersOwnEmployeeIdByDefault() {
        service.list(null, null, null);

        verify(leave)
                .pendingForManager(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(callerEmployeeId),
                        any());
        verify(timesheets)
                .pendingForManager(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(callerEmployeeId),
                        any());
        verify(performance).pendingForManager(tenantId, callerEmployeeId);
    }

    @Test
    void listMergesAndOrdersAcrossModulesByPendingSinceAscending() {
        UUID employeeA = UUID.randomUUID();
        UUID employeeB = UUID.randomUUID();
        Instant earlier = Instant.parse("2026-08-01T00:00:00Z");
        Instant later = Instant.parse("2026-08-05T00:00:00Z");

        when(leave.pendingForManager(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(leaveResponse(employeeA, later))));
        when(timesheets.pendingForManager(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(timesheetResponse(employeeB, earlier))));

        ApprovalsPageResponse page = service.list(null, null, null);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).sourceModule()).isEqualTo(ApprovalSourceModule.TIMESHEET);
        assertThat(page.items().get(1).sourceModule()).isEqualTo(ApprovalSourceModule.LEAVE);
    }

    @Test
    void listThrowsNotFoundWhenActingForEmployeeHasNoActiveDelegationToCaller() {
        UUID peerEmployeeId = UUID.randomUUID();
        Employee peer = new Employee();
        peer.setId(peerEmployeeId);
        peer.setUserId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(peerEmployeeId, tenantId)).thenReturn(Optional.of(peer));
        when(delegations.isActiveDelegateOf(tenantId, peer.getUserId(), callerUserId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.list(peerEmployeeId, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(accessLog)
                .logDenied(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(callerEmployeeId),
                        org.mockito.ArgumentMatchers.eq(peerEmployeeId),
                        any(),
                        any());
        verify(leave, never()).pendingForManager(any(), any(), any());
    }

    @Test
    void listSucceedsAndQueriesThePeersInboxWhenActiveDelegationExists() {
        UUID peerEmployeeId = UUID.randomUUID();
        Employee peer = new Employee();
        peer.setId(peerEmployeeId);
        peer.setUserId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(peerEmployeeId, tenantId)).thenReturn(Optional.of(peer));
        when(delegations.isActiveDelegateOf(tenantId, peer.getUserId(), callerUserId))
                .thenReturn(true);

        ApprovalsPageResponse page = service.list(peerEmployeeId, null, null);

        assertThat(page.actingForDelegator()).isTrue();
        verify(leave)
                .pendingForManager(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(peerEmployeeId),
                        any());
        verify(accessLog)
                .logGranted(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(callerEmployeeId),
                        org.mockito.ArgumentMatchers.eq(peerEmployeeId),
                        any());
    }

    @Test
    void decideApproveDispatchesToLeaveRequestService() {
        UUID id = UUID.randomUUID();
        when(leave.approve(any(), any(), any()))
                .thenReturn(leaveResponse(UUID.randomUUID(), Instant.now()));

        var result =
                service.decide(ApprovalSourceModule.LEAVE, id, null, ApprovalAction.APPROVE, "ok");

        verify(leave).approve(tenantId, id, new DecideLeaveRequestRequest("ok"));
        assertThat(result.sourceModule()).isEqualTo(ApprovalSourceModule.LEAVE);
    }

    @Test
    void decideRejectDispatchesToTimesheetService() {
        UUID id = UUID.randomUUID();
        when(timesheets.reject(any(), any(), any()))
                .thenReturn(timesheetResponse(UUID.randomUUID(), Instant.now()));

        service.decide(ApprovalSourceModule.TIMESHEET, id, null, ApprovalAction.REJECT, "no good");

        verify(timesheets).reject(tenantId, id, new DecideTimesheetRequest("no good"));
    }

    @Test
    void decideRejectsActOnReadOnlyModules() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.decide(
                                        ApprovalSourceModule.PERFORMANCE,
                                        id,
                                        null,
                                        ApprovalAction.APPROVE,
                                        null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(performance, never()).submitManager(any(), any(), any());
    }

    @Test
    void bulkActReportsIndependentSuccessAndFailurePerItem() {
        UUID okLeaveId = UUID.randomUUID();
        UUID badTimesheetId = UUID.randomUUID();
        when(leave.approve(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(okLeaveId),
                        any()))
                .thenReturn(leaveResponse(UUID.randomUUID(), Instant.now()));
        when(timesheets.approve(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(badTimesheetId),
                        any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Timesheet not found"));

        BulkApprovalActionRequest request =
                new BulkApprovalActionRequest(
                        List.of(
                                new BulkApprovalItemRequest(
                                        ApprovalSourceModule.LEAVE,
                                        okLeaveId,
                                        ApprovalAction.APPROVE,
                                        null),
                                new BulkApprovalItemRequest(
                                        ApprovalSourceModule.TIMESHEET,
                                        badTimesheetId,
                                        ApprovalAction.APPROVE,
                                        null)));

        BulkApprovalActionResponse response = service.bulkAct(request, null);

        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.results()).hasSize(2);
        assertThat(response.results().get(0).status()).isEqualTo("SUCCESS");
        assertThat(response.results().get(1).status()).isEqualTo("FAILED");
        // Enumeration protection: the raw "Timesheet not found" message must not leak through.
        assertThat(response.results().get(1).error()).isEqualTo("Not found or not authorized");
        verify(leave, times(1)).approve(any(), any(), any());
        verify(timesheets, times(1)).approve(any(), any(), any());
    }

    @Test
    void bulkActContinuesToLaterItemsAfterAnEarlierItemThrows() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(leave.approve(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(firstId),
                        any()))
                .thenThrow(
                        new ApiException(
                                HttpStatus.FORBIDDEN, "You are not this employee's manager"));
        when(leave.approve(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(secondId),
                        any()))
                .thenReturn(leaveResponse(UUID.randomUUID(), Instant.now()));

        BulkApprovalActionRequest request =
                new BulkApprovalActionRequest(
                        List.of(
                                new BulkApprovalItemRequest(
                                        ApprovalSourceModule.LEAVE,
                                        firstId,
                                        ApprovalAction.APPROVE,
                                        null),
                                new BulkApprovalItemRequest(
                                        ApprovalSourceModule.LEAVE,
                                        secondId,
                                        ApprovalAction.APPROVE,
                                        null)));

        BulkApprovalActionResponse response = service.bulkAct(request, null);

        assertThat(response.succeeded()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        verify(leave).approve(tenantId, secondId, new DecideLeaveRequestRequest(null));
    }

    private static LeaveRequestResponse leaveResponse(UUID employeeId, Instant submittedAt) {
        return new LeaveRequestResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                employeeId,
                UUID.randomUUID(),
                "ANNUAL",
                java.time.LocalDate.now(),
                java.time.LocalDate.now().plusDays(1),
                java.math.BigDecimal.ONE,
                "reason",
                LeaveRequestStatus.SUBMITTED,
                submittedAt,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                submittedAt,
                submittedAt,
                null,
                null,
                0L);
    }

    private static TimesheetResponse timesheetResponse(UUID employeeId, Instant submittedAt) {
        return new TimesheetResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                employeeId,
                UUID.randomUUID(),
                java.time.LocalDate.now(),
                java.time.LocalDate.now(),
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                TimesheetStatus.SUBMITTED,
                submittedAt,
                null,
                null,
                null,
                null,
                null,
                null,
                submittedAt,
                submittedAt,
                null,
                null,
                0L);
    }
}
