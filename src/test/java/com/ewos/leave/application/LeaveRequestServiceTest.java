package com.ewos.leave.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.LeaveMapper;
import com.ewos.leave.api.dto.DecideLeaveRequestRequest;
import com.ewos.leave.domain.LeaveBalance;
import com.ewos.leave.domain.LeaveBalanceCalculator;
import com.ewos.leave.domain.LeavePolicy;
import com.ewos.leave.domain.LeaveRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
import com.ewos.leave.domain.LeaveType;
import com.ewos.leave.infrastructure.persistence.LeaveRequestRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 4 audit fix #4 regression coverage: {@code LEAVE_APPROVE} used to be a flat platform
 * permission with no server-side check that the approver is the target employee's manager. These
 * tests exercise {@code requireManagerAuthorityUnlessAdmin} directly through {@code approve}/{@code
 * reject}, since that's the one piece of genuinely new authorization logic this fix introduces —
 * everything else in the class is untouched Sprint 1/2 behaviour.
 */
@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock LeaveRequestRepository requests;
    @Mock EmployeeRepository employees;
    @Mock LeaveTypeService leaveTypes;
    @Mock LeaveBalanceService balances;
    @Mock LeaveBalanceCalculator calculator;
    @Mock WorkflowInstanceService workflow;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private LeaveRequestService service;
    private UUID tenantId;
    private UUID actorUserId;
    private UUID employeeId;
    private UUID managerId;

    @BeforeEach
    void setUp() {
        service =
                new LeaveRequestService(
                        requests,
                        employees,
                        leaveTypes,
                        balances,
                        calculator,
                        new LeavePolicy(),
                        workflow,
                        new LeaveMapper(calculator),
                        events,
                        Clock.fixed(java.time.Instant.now(), ZoneOffset.UTC),
                        guard);
        tenantId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actorUserId.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private LeaveRequest submittedRequest(UUID managerEmployeeIdOrNull) {
        Employee manager = null;
        if (managerEmployeeIdOrNull != null) {
            manager = new Employee();
            manager.setId(managerEmployeeIdOrNull);
        }
        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setManager(manager);

        LeaveType type = new LeaveType();
        type.setId(UUID.randomUUID());

        LeaveRequest r = new LeaveRequest();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(UUID.randomUUID());
        r.setEmployee(employee);
        r.setLeaveType(type);
        r.setStartDate(LocalDate.now());
        r.setEndDate(LocalDate.now().plusDays(1));
        r.setDaysRequested(BigDecimal.ONE);
        r.setStatus(LeaveRequestStatus.SUBMITTED);
        return r;
    }

    private void grantLeaveAdmin() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                actorUserId.toString(),
                                null,
                                List.of(new SimpleGrantedAuthority("LEAVE_ADMIN"))));
    }

    private void stubBalance(LeaveRequest r) {
        LeaveBalance balance = new LeaveBalance();
        balance.setPendingDays(BigDecimal.TEN);
        balance.setConsumedDays(BigDecimal.ZERO);
        lenient()
                .when(
                        balances.requireBalanceForType(
                                org.mockito.ArgumentMatchers.eq(tenantId),
                                org.mockito.ArgumentMatchers.eq(employeeId),
                                any(),
                                org.mockito.ArgumentMatchers.anyInt(),
                                any()))
                .thenReturn(balance);
    }

    @Test
    void approveRejectsWhenActorIsNeitherManagerNorAdmin() {
        LeaveRequest r = submittedRequest(managerId);
        when(requests.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId, r.getId(), new DecideLeaveRequestRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        org.mockito.Mockito.verify(balances, never())
                .requireBalanceForType(
                        any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void approveRejectsWhenRequestHasNoManagerOnRecord() {
        LeaveRequest r = submittedRequest(null);
        when(requests.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId, r.getId(), new DecideLeaveRequestRequest(null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void approveSucceedsWhenActorIsTheEmployeesManager() {
        LeaveRequest r = submittedRequest(managerId);
        when(requests.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        Employee managerRecord = new Employee();
        managerRecord.setId(managerId);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId))
                .thenReturn(List.of(managerRecord));
        stubBalance(r);

        service.approve(tenantId, r.getId(), new DecideLeaveRequestRequest(null));
    }

    @Test
    void approveSucceedsForLeaveAdminRegardlessOfManagerRelationship() {
        grantLeaveAdmin();
        LeaveRequest r = submittedRequest(managerId);
        when(requests.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        stubBalance(r);

        service.approve(tenantId, r.getId(), new DecideLeaveRequestRequest(null));

        org.mockito.Mockito.verify(employees, never()).findAllByUserIdAndTenantId(any(), any());
    }

    @Test
    void rejectRejectsWhenActorIsNeitherManagerNorAdmin() {
        LeaveRequest r = submittedRequest(managerId);
        when(requests.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId)).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.reject(
                                        tenantId, r.getId(), new DecideLeaveRequestRequest("no")))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectSucceedsWhenActorIsTheEmployeesManager() {
        LeaveRequest r = submittedRequest(managerId);
        when(requests.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        Employee managerRecord = new Employee();
        managerRecord.setId(managerId);
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId))
                .thenReturn(List.of(managerRecord));
        stubBalance(r);

        service.reject(tenantId, r.getId(), new DecideLeaveRequestRequest("Not eligible"));
    }
}
