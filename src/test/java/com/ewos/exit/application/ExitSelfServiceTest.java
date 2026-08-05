package com.ewos.exit.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.exit.api.dto.CreateResignationRequest;
import com.ewos.exit.api.dto.ResignationResponse;
import com.ewos.exit.api.dto.SelfResignationRequest;
import com.ewos.exit.domain.ResignationStatus;
import com.ewos.exit.domain.ResignationType;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
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
class ExitSelfServiceTest {

    @Mock ExitService exit;
    @Mock EmployeeRepository employees;
    @Mock TenantContext tenantContext;
    @Mock EmployeeContext employeeContext;

    private ExitSelfService service;

    @BeforeEach
    void setUp() {
        service = new ExitSelfService(exit, employees, tenantContext, employeeContext);
    }

    private static ResignationResponse response(UUID id, UUID employeeId) {
        return new ResignationResponse(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                employeeId,
                ResignationType.SELF_RESIGNATION,
                null,
                null,
                LocalDate.now(),
                "reason",
                30,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                ResignationStatus.SUBMITTED,
                null,
                null,
                null);
    }

    @Test
    void submitMyResignationResolvesCompanyIdFromTheCallersEmployeeRecord() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Employee employee = new Employee();
        employee.setCompanyId(companyId);
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));

        LocalDate lastDay = LocalDate.now().plusDays(30);
        service.submitMyResignation(new SelfResignationRequest(lastDay, "career", 30));

        verify(exit)
                .submitSelf(
                        eq(tenantId),
                        eq(
                                new CreateResignationRequest(
                                        companyId,
                                        employeeId,
                                        ResignationType.SELF_RESIGNATION,
                                        lastDay,
                                        "career",
                                        30)));
    }

    @Test
    void submitMyResignationRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.submitMyResignation(
                                        new SelfResignationRequest(LocalDate.now(), "r", 30)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myResignationsDelegatesScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myResignations();

        verify(exit).resignationsForEmployee(tenantId, employeeId);
    }

    @Test
    void withdrawMyResignationRejectsWhenResignationBelongsToSomeoneElse() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID otherEmployeeId = UUID.randomUUID();
        UUID resignationId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(exit.getResignation(tenantId, resignationId))
                .thenReturn(response(resignationId, otherEmployeeId));

        assertThatThrownBy(() -> service.withdrawMyResignation(resignationId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(exit, never()).withdraw(any(), any());
    }

    @Test
    void withdrawMyResignationDelegatesWhenOwnedByCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID resignationId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(exit.getResignation(tenantId, resignationId))
                .thenReturn(response(resignationId, employeeId));

        service.withdrawMyResignation(resignationId);

        verify(exit).withdraw(tenantId, resignationId);
    }
}
