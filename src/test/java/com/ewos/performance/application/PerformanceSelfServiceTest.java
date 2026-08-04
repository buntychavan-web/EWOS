package com.ewos.performance.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.performance.api.dto.ManagerAssessmentRequest;
import com.ewos.performance.api.dto.ReviewerAssessmentRequest;
import com.ewos.performance.api.dto.SelfAssessmentRequest;
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
class PerformanceSelfServiceTest {

    @Mock AppraisalService appraisals;
    @Mock TenantContext tenantContext;
    @Mock EmployeeContext employeeContext;

    private PerformanceSelfService service;

    @BeforeEach
    void setUp() {
        service = new PerformanceSelfService(appraisals, tenantContext, employeeContext);
    }

    @Test
    void myAppraisalsRequiresLinkedEmployee() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.myAppraisals())
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myAppraisalsDelegatesToForEmployeeScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myAppraisals();

        verify(appraisals).forEmployee(tenantId, employeeId);
    }

    @Test
    void myAppraisalDelegatesToForParticipantScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID appraisalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.myAppraisal(appraisalId);

        verify(appraisals).forParticipant(tenantId, appraisalId, employeeId);
    }

    @Test
    void submitMySelfAssessmentRequiresLinkedEmployeeBeforeDelegating() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.submitMySelfAssessment(
                                        UUID.randomUUID(),
                                        new SelfAssessmentRequest(BigDecimal.valueOf(4), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void submitMySelfAssessmentDelegatesToAppraisalServiceSubmitSelf() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID appraisalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        SelfAssessmentRequest req = new SelfAssessmentRequest(BigDecimal.valueOf(4), "good work");

        service.submitMySelfAssessment(appraisalId, req);

        verify(appraisals).submitSelf(tenantId, appraisalId, req);
    }

    @Test
    void pendingMyManagerReviewDelegatesToPendingForManagerScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.pendingMyManagerReview();

        verify(appraisals).pendingForManager(tenantId, employeeId);
    }

    @Test
    void submitMyManagerAssessmentDelegatesToAppraisalServiceSubmitManager() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID appraisalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        ManagerAssessmentRequest req = new ManagerAssessmentRequest(BigDecimal.valueOf(3), null);

        service.submitMyManagerAssessment(appraisalId, req);

        verify(appraisals).submitManager(tenantId, appraisalId, req);
    }

    @Test
    void pendingMyReviewerReviewDelegatesToPendingForReviewerScopedToCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));

        service.pendingMyReviewerReview();

        verify(appraisals).pendingForReviewer(tenantId, employeeId);
    }

    @Test
    void submitMyReviewerAssessmentDelegatesToAppraisalServiceSubmitReviewer() {
        UUID tenantId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID appraisalId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        ReviewerAssessmentRequest req = new ReviewerAssessmentRequest(BigDecimal.valueOf(5), null);

        service.submitMyReviewerAssessment(appraisalId, req);

        verify(appraisals).submitReviewer(tenantId, appraisalId, req);
    }
}
