package com.ewos.onboarding.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class OnboardingSelfServiceTest {

    @Mock OnboardingPlanService plans;
    @Mock TenantContext tenantContext;
    @Mock EmployeeContext employeeContext;

    private OnboardingSelfService service;

    @BeforeEach
    void setUp() {
        service = new OnboardingSelfService(plans, tenantContext, employeeContext);
    }

    @Test
    void myPlanRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myPlan())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myPlanDelegatesToForEmployeeScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myPlan();

        verify(plans).forEmployee(tenantId, employeeId);
    }

    @Test
    void myTasksRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myTasks())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myTasksDelegatesToTasksForEmployeeScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myTasks();

        verify(plans).tasksForEmployee(tenantId, employeeId);
    }
}
