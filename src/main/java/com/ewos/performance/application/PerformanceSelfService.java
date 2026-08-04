package com.ewos.performance.application;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.performance.api.dto.AppraisalResponse;
import com.ewos.performance.api.dto.ManagerAssessmentRequest;
import com.ewos.performance.api.dto.ReviewerAssessmentRequest;
import com.ewos.performance.api.dto.SelfAssessmentRequest;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Employee Self-Service over the existing Performance module (Sprint 24A) — mirrors {@code
 * OnboardingSelfService} (Sprint 23A) and {@code LeaveSelfService} (Sprint 3) exactly: every method
 * resolves the caller's own tenant/employee from {@link TenantContext}/{@link EmployeeContext} and
 * delegates entirely to {@link AppraisalService}, which itself scopes each lookup with the
 * self-service-aware {@code ClientAccessGuard} overload plus the object-level ownership check added
 * in this sprint — no new business logic lives here.
 */
@Service
public class PerformanceSelfService {

    private final AppraisalService appraisals;
    private final TenantContext tenantContext;
    private final EmployeeContext employeeContext;

    public PerformanceSelfService(
            AppraisalService appraisals,
            TenantContext tenantContext,
            EmployeeContext employeeContext) {
        this.appraisals = appraisals;
        this.tenantContext = tenantContext;
        this.employeeContext = employeeContext;
    }

    public List<AppraisalResponse> myAppraisals() {
        return appraisals.forEmployee(tenantContext.homeTenantId(), requireEmployeeId());
    }

    public AppraisalResponse myAppraisal(UUID id) {
        return appraisals.forParticipant(tenantContext.homeTenantId(), id, requireEmployeeId());
    }

    public AppraisalResponse submitMySelfAssessment(UUID id, SelfAssessmentRequest req) {
        requireEmployeeId();
        return appraisals.submitSelf(tenantContext.homeTenantId(), id, req);
    }

    public List<AppraisalResponse> pendingMyManagerReview() {
        return appraisals.pendingForManager(tenantContext.homeTenantId(), requireEmployeeId());
    }

    public AppraisalResponse submitMyManagerAssessment(UUID id, ManagerAssessmentRequest req) {
        requireEmployeeId();
        return appraisals.submitManager(tenantContext.homeTenantId(), id, req);
    }

    public List<AppraisalResponse> pendingMyReviewerReview() {
        return appraisals.pendingForReviewer(tenantContext.homeTenantId(), requireEmployeeId());
    }

    public AppraisalResponse submitMyReviewerAssessment(UUID id, ReviewerAssessmentRequest req) {
        requireEmployeeId();
        return appraisals.submitReviewer(tenantContext.homeTenantId(), id, req);
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
}
