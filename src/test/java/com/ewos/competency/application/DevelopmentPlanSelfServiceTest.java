package com.ewos.competency.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.competency.api.dto.PlanResponse;
import com.ewos.employee.application.EmployeeContext;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DevelopmentPlanSelfServiceTest {

    @Mock DevelopmentPlanService plans;
    @Mock TenantContext tenantContext;
    @Mock EmployeeContext employeeContext;

    private DevelopmentPlanSelfService service;

    @BeforeEach
    void setUp() {
        service = new DevelopmentPlanSelfService(plans, tenantContext, employeeContext);
    }

    private static PlanResponse planOwnedBy(UUID id, UUID employeeId) {
        return new PlanResponse(id, null, null, employeeId, null, null, null, null, null, null);
    }

    @Test
    void myPlansRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myPlans())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myPlansDelegatesToForEmployeeScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myPlans();

        verify(plans).forEmployee(tenantId, employeeId);
    }

    @Test
    void myPlanActionsRejectsPlanOwnedByAnotherEmployee() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(plans.getById(tenantId, planId)).thenReturn(planOwnedBy(planId, UUID.randomUUID()));

        assertThatThrownBy(() -> service.myPlanActions(planId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(plans, never()).actionsFor(tenantId, planId);
    }

    @Test
    void myPlanActionsDelegatesWhenPlanOwnedByCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(plans.getById(tenantId, planId)).thenReturn(planOwnedBy(planId, employeeId));

        service.myPlanActions(planId);

        verify(plans).actionsFor(tenantId, planId);
    }

    @Test
    void completeMyActionRejectsActionOwnedByAnotherEmployee() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(plans.employeeIdForAction(tenantId, actionId)).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.completeMyAction(actionId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(plans, never()).completeAction(tenantId, actionId);
    }

    @Test
    void completeMyActionRejectsActionWithNoResolvableOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(plans.employeeIdForAction(tenantId, actionId)).thenReturn(null);

        assertThatThrownBy(() -> service.completeMyAction(actionId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void completeMyActionDelegatesWhenActionOwnedByCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID actionId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(plans.employeeIdForAction(tenantId, actionId)).thenReturn(employeeId);

        service.completeMyAction(actionId);

        verify(plans).completeAction(tenantId, actionId);
    }
}
