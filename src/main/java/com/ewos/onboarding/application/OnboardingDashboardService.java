package com.ewos.onboarding.application;

import com.ewos.onboarding.api.dto.OnboardingDashboardResponse;
import com.ewos.onboarding.api.dto.OnboardingReportRowResponse;
import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.OnboardingPlanStatus;
import com.ewos.onboarding.domain.OnboardingTaskInstance;
import com.ewos.onboarding.infrastructure.persistence.OnboardingPlanRepository;
import com.ewos.onboarding.infrastructure.persistence.OnboardingTaskInstanceRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Aggregate reads over onboarding plans and tasks for dashboards + reports. */
@Service
@Transactional(readOnly = true)
public class OnboardingDashboardService {

    private final OnboardingPlanRepository plans;
    private final OnboardingTaskInstanceRepository tasks;
    private final ClientAccessGuard guard;

    public OnboardingDashboardService(
            OnboardingPlanRepository plans,
            OnboardingTaskInstanceRepository tasks,
            ClientAccessGuard guard) {
        this.plans = plans;
        this.tasks = tasks;
        this.guard = guard;
    }

    public OnboardingDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        long planned =
                plans.findAllByTenantIdAndCompanyIdAndStatus(
                                tenantId, companyId, OnboardingPlanStatus.PLANNED)
                        .size();
        long inProgress =
                plans.findAllByTenantIdAndCompanyIdAndStatus(
                                tenantId, companyId, OnboardingPlanStatus.IN_PROGRESS)
                        .size();
        long completed =
                plans.findAllByTenantIdAndCompanyIdAndStatus(
                                tenantId, companyId, OnboardingPlanStatus.COMPLETED)
                        .size();
        long cancelled =
                plans.findAllByTenantIdAndCompanyIdAndStatus(
                                tenantId, companyId, OnboardingPlanStatus.CANCELLED)
                        .size();
        return new OnboardingDashboardResponse(
                planned,
                inProgress,
                completed,
                cancelled,
                List.of(
                        new OnboardingDashboardResponse.PlanStatusBucket("PLANNED", planned),
                        new OnboardingDashboardResponse.PlanStatusBucket("IN_PROGRESS", inProgress),
                        new OnboardingDashboardResponse.PlanStatusBucket("COMPLETED", completed),
                        new OnboardingDashboardResponse.PlanStatusBucket("CANCELLED", cancelled)));
    }

    public List<OnboardingReportRowResponse> reportByStatus(
            UUID tenantId, UUID companyId, OnboardingPlanStatus status) {
        guard.requireAccessForCompany(companyId);
        return plans.findAllByTenantIdAndCompanyIdAndStatus(tenantId, companyId, status).stream()
                .map(p -> summarise(tenantId, p))
                .toList();
    }

    private OnboardingReportRowResponse summarise(UUID tenantId, OnboardingPlan p) {
        List<OnboardingTaskInstance> all =
                tasks.findAllByTenantIdAndPlanIdOrderBySortOrderAsc(tenantId, p.getId());
        long completed = all.stream().filter(OnboardingTaskInstance::isTerminal).count();
        long outstandingMandatory =
                all.stream().filter(t -> t.isMandatory() && !t.isTerminal()).count();
        return new OnboardingReportRowResponse(
                p.getId(),
                p.getEmployee() == null ? null : p.getEmployee().getId(),
                p.getJoiningDate(),
                p.getStatus(),
                p.getCompletionPercent(),
                all.size(),
                completed,
                outstandingMandatory);
    }
}
