package com.ewos.probation.application;

import com.ewos.onboarding.domain.OnboardingPlan;
import com.ewos.onboarding.domain.events.OnboardingEvent;
import com.ewos.onboarding.domain.events.OnboardingEventType;
import com.ewos.onboarding.infrastructure.persistence.OnboardingPlanRepository;
import com.ewos.probation.api.dto.OpenProbationRequest;
import com.ewos.probation.infrastructure.persistence.ProbationRecordRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ties T5 onboarding to T6 probation. When an onboarding plan reaches {@code COMPLETED} and the
 * employee has a joining date, this listener opens a {@link
 * com.ewos.probation.domain.ProbationRecord probation record} for them — one record per employee,
 * idempotent.
 */
@Component
public class OnboardingPlanCompletedListener {

    private static final Logger LOG =
            LoggerFactory.getLogger(OnboardingPlanCompletedListener.class);

    private final ProbationService probation;
    private final ProbationRecordRepository records;
    private final OnboardingPlanRepository plans;

    public OnboardingPlanCompletedListener(
            ProbationService probation,
            ProbationRecordRepository records,
            OnboardingPlanRepository plans) {
        this.probation = probation;
        this.records = records;
        this.plans = plans;
    }

    @EventListener
    @Transactional
    public void onOnboardingEvent(OnboardingEvent event) {
        if (event.eventType() != OnboardingEventType.PLAN_COMPLETED) {
            return;
        }
        UUID planId = event.planId();
        UUID employeeId = event.employeeId();
        if (planId == null && employeeId == null) {
            LOG.warn(
                    "PLAN_COMPLETED event tenant={} arrived without planId or employeeId",
                    event.tenantId());
            return;
        }

        UUID resolvedEmployeeId = employeeId;
        if (resolvedEmployeeId == null) {
            OnboardingPlan plan = plans.findByIdAndTenantId(planId, event.tenantId()).orElse(null);
            if (plan == null || plan.getEmployee() == null) {
                LOG.info(
                        "Skipping probation open — plan {} tenant={} not found or has no employee",
                        planId,
                        event.tenantId());
                return;
            }
            resolvedEmployeeId = plan.getEmployee().getId();
        }

        if (records.findByTenantIdAndEmployeeId(event.tenantId(), resolvedEmployeeId).isPresent()) {
            LOG.debug("Probation record already exists for employee={}", resolvedEmployeeId);
            return;
        }

        OnboardingPlan plan =
                planId == null
                        ? null
                        : plans.findByIdAndTenantId(planId, event.tenantId()).orElse(null);
        java.time.LocalDate periodStart =
                plan == null || plan.getJoiningDate() == null
                        ? java.time.LocalDate.now()
                        : plan.getJoiningDate();

        try {
            probation.open(
                    new OpenProbationRequest(
                            event.tenantId(),
                            event.companyId(),
                            resolvedEmployeeId,
                            null,
                            periodStart,
                            null));
        } catch (RuntimeException e) {
            LOG.warn(
                    "Failed to auto-open probation for employee={} tenant={}: {}",
                    resolvedEmployeeId,
                    event.tenantId(),
                    e.getMessage());
        }
    }
}
