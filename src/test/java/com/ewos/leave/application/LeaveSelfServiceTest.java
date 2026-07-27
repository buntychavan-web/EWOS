package com.ewos.leave.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.leave.api.dto.CreateLeaveRequestRequest;
import com.ewos.leave.api.dto.LeaveRequestResponse;
import com.ewos.leave.api.dto.SelfLeaveRequestRequest;
import com.ewos.leave.api.dto.SubmitLeaveRequestRequest;
import com.ewos.leave.domain.LeaveRequestStatus;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.workflow.application.WorkflowDefinitionService;
import com.ewos.workflow.domain.WorkflowDefinition;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class LeaveSelfServiceTest {

    @Mock LeaveRequestService requests;
    @Mock LeaveBalanceService balances;
    @Mock LeaveTypeService leaveTypes;
    @Mock EmployeeRepository employees;
    @Mock TenantContext tenantContext;
    @Mock EmployeeContext employeeContext;
    @Mock WorkflowDefinitionService workflowDefinitions;

    private LeaveSelfService service;

    @BeforeEach
    void setUp() {
        service =
                new LeaveSelfService(
                        requests,
                        balances,
                        leaveTypes,
                        employees,
                        tenantContext,
                        employeeContext,
                        workflowDefinitions);
    }

    private static LeaveRequestResponse response(UUID id, UUID employeeId) {
        return new LeaveRequestResponse(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                employeeId,
                UUID.randomUUID(),
                "ANNUAL",
                LocalDate.now(),
                LocalDate.now().plusDays(1),
                BigDecimal.ONE,
                "reason",
                LeaveRequestStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0L);
    }

    @Test
    void myRequestsRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myRequests())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myRequestsDelegatesToForEmployeeScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myRequests();

        verify(requests).forEmployee(tenantId, employeeId);
    }

    @Test
    void createMyRequestResolvesCompanyIdFromTheCallersEmployeeRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID leaveTypeId = UUID.randomUUID();
        Employee employee = new Employee();
        employee.setCompanyId(companyId);
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        LocalDate start = LocalDate.now().plusDays(5);
        LocalDate end = LocalDate.now().plusDays(6);
        service.createMyRequest(new SelfLeaveRequestRequest(leaveTypeId, start, end, "vacation"));

        verify(requests)
                .create(
                        eq(
                                new CreateLeaveRequestRequest(
                                        tenantId,
                                        companyId,
                                        employeeId,
                                        leaveTypeId,
                                        start,
                                        end,
                                        "vacation")));
    }

    @Test
    void submitMyRequestRejectsWhenRequestBelongsToSomeoneElse() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID otherEmployeeId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(requests.getById(tenantId, requestId))
                .thenReturn(response(requestId, otherEmployeeId));

        assertThatThrownBy(() -> service.submitMyRequest(requestId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void submitMyRequestResolvesWorkflowDefinitionAndDelegatesWhenOwnedByCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID workflowDefinitionId = UUID.randomUUID();
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setId(workflowDefinitionId);
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(requests.getById(tenantId, requestId)).thenReturn(response(requestId, employeeId));
        when(workflowDefinitions.findEffective(tenantId, LeaveRequestService.SUBJECT_TYPE))
                .thenReturn(definition);

        service.submitMyRequest(requestId);

        verify(requests)
                .submit(
                        eq(tenantId),
                        eq(requestId),
                        eq(new SubmitLeaveRequestRequest(workflowDefinitionId)));
    }

    @Test
    void cancelMyRequestRejectsWhenRequestBelongsToSomeoneElse() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID otherEmployeeId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(requests.getById(tenantId, requestId))
                .thenReturn(response(requestId, otherEmployeeId));

        assertThatThrownBy(() -> service.cancelMyRequest(requestId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(requests, never()).cancel(any(), any(), eq(false));
    }

    @Test
    void cancelMyRequestDelegatesAsNonAdminWhenOwnedByCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(requests.getById(tenantId, requestId)).thenReturn(response(requestId, employeeId));

        service.cancelMyRequest(requestId);

        verify(requests).cancel(tenantId, requestId, false);
    }

    @Test
    void myBalancesDefaultsToCurrentYearWhenNotSpecified() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myBalances(null);

        verify(balances).balancesForEmployee(tenantId, employeeId, LocalDate.now().getYear());
    }

    @Test
    void myBalancesUsesTheProvidedYear() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myBalances(2024);

        verify(balances).balancesForEmployee(tenantId, employeeId, 2024);
    }

    @Test
    void leaveTypesDelegatesToTheExistingCatalogList() {
        UUID tenantId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);

        service.leaveTypes();

        verify(leaveTypes).list(tenantId);
    }
}
