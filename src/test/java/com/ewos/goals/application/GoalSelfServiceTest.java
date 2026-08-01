package com.ewos.goals.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.goals.api.dto.GoalResponse;
import com.ewos.goals.api.dto.ProgressUpdateRequest;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class GoalSelfServiceTest {

    @Mock GoalService goals;
    @Mock TenantContext tenantContext;
    @Mock EmployeeContext employeeContext;

    private GoalSelfService service;

    @BeforeEach
    void setUp() {
        service = new GoalSelfService(goals, tenantContext, employeeContext);
    }

    private static GoalResponse goalOwnedBy(UUID id, UUID employeeId) {
        return new GoalResponse(
                id, // id
                null, // tenantId
                null, // companyId
                null, // libraryGoalId
                null, // parentGoalId
                null, // code
                null, // name
                null, // description
                null, // goalType
                null, // scope
                employeeId, // employeeId
                null, // orgUnitId
                null, // performanceCycleId
                null, // periodStart
                null, // periodEnd
                null, // weightage
                null, // target
                null, // unitOfMeasure
                null, // currentValue
                null, // progressPercent
                null, // status
                null, // priority
                null, // reviewScore
                null, // reviewNotes
                null, // reviewedAt
                null, // reviewedBy
                null, // closedAt
                null); // closedBy
    }

    @Test
    void myGoalsRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myGoals())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myGoalsDelegatesToByEmployeeScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myGoals();

        verify(goals).byEmployee(tenantId, employeeId);
    }

    @Test
    void recordMyProgressRejectsGoalOwnedByAnotherEmployee() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(goals.getById(tenantId, goalId)).thenReturn(goalOwnedBy(goalId, UUID.randomUUID()));
        ProgressUpdateRequest req = new ProgressUpdateRequest("50", BigDecimal.valueOf(50), null);

        assertThatThrownBy(() -> service.recordMyProgress(goalId, req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(goals, org.mockito.Mockito.never()).recordProgress(any(), any(), any());
    }

    @Test
    void recordMyProgressDelegatesWhenGoalOwnedByCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(goals.getById(tenantId, goalId)).thenReturn(goalOwnedBy(goalId, employeeId));
        ProgressUpdateRequest req = new ProgressUpdateRequest("50", BigDecimal.valueOf(50), null);

        service.recordMyProgress(goalId, req);

        verify(goals).recordProgress(tenantId, goalId, req);
    }

    @Test
    void submitMyGoalForReviewRejectsGoalOwnedByAnotherEmployee() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(goals.getById(tenantId, goalId)).thenReturn(goalOwnedBy(goalId, UUID.randomUUID()));

        assertThatThrownBy(() -> service.submitMyGoalForReview(goalId))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(goals, org.mockito.Mockito.never()).submitForReview(any(), any());
    }

    @Test
    void submitMyGoalForReviewDelegatesWhenGoalOwnedByCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID goalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(goals.getById(tenantId, goalId)).thenReturn(goalOwnedBy(goalId, employeeId));

        service.submitMyGoalForReview(goalId);

        verify(goals).submitForReview(tenantId, goalId);
    }
}
