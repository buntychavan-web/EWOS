package com.ewos.onboarding.domain;

import com.ewos.shared.exception.ApiException;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Lifecycle guards for onboarding plans and tasks. */
@Component
public class OnboardingPolicy {

    private static final Set<OnboardingPlanStatus> TERMINAL_PLAN =
            EnumSet.of(OnboardingPlanStatus.COMPLETED, OnboardingPlanStatus.CANCELLED);

    public void assertPlanStartable(OnboardingPlan p) {
        if (p.getStatus() != OnboardingPlanStatus.PLANNED) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Plan must be PLANNED to start (current: " + p.getStatus() + ")");
        }
    }

    public void assertPlanCompletable(OnboardingPlan p) {
        if (p.getStatus() != OnboardingPlanStatus.IN_PROGRESS) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Plan must be IN_PROGRESS to complete (current: " + p.getStatus() + ")");
        }
    }

    public void assertPlanCancellable(OnboardingPlan p) {
        if (TERMINAL_PLAN.contains(p.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Plan is already terminal (current: " + p.getStatus() + ")");
        }
    }

    public void assertPlanMutable(OnboardingPlan p) {
        if (TERMINAL_PLAN.contains(p.getStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Plan is already terminal — cannot modify (" + p.getStatus() + ")");
        }
    }

    public void assertTaskEditable(OnboardingTaskInstance t) {
        if (t.isTerminal()) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Task is already terminal (" + t.getStatus() + ")");
        }
    }

    public boolean isPlanTerminal(OnboardingPlanStatus status) {
        return TERMINAL_PLAN.contains(status);
    }
}
