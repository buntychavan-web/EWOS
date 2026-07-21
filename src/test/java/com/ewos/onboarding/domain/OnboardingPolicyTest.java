package com.ewos.onboarding.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class OnboardingPolicyTest {

    private final OnboardingPolicy policy = new OnboardingPolicy();

    @Test
    void plannedIsStartable() {
        OnboardingPlan p = plan(OnboardingPlanStatus.PLANNED);
        assertThatCode(() -> policy.assertPlanStartable(p)).doesNotThrowAnyException();
    }

    @Test
    void inProgressNotStartable() {
        OnboardingPlan p = plan(OnboardingPlanStatus.IN_PROGRESS);
        assertThatThrownBy(() -> policy.assertPlanStartable(p)).isInstanceOf(ApiException.class);
    }

    @Test
    void inProgressCompletable() {
        OnboardingPlan p = plan(OnboardingPlanStatus.IN_PROGRESS);
        assertThatCode(() -> policy.assertPlanCompletable(p)).doesNotThrowAnyException();
    }

    @Test
    void completedNotCancellable() {
        OnboardingPlan p = plan(OnboardingPlanStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertPlanCancellable(p)).isInstanceOf(ApiException.class);
    }

    @Test
    void plannedMutable() {
        OnboardingPlan p = plan(OnboardingPlanStatus.PLANNED);
        assertThatCode(() -> policy.assertPlanMutable(p)).doesNotThrowAnyException();
    }

    @Test
    void completedNotMutable() {
        OnboardingPlan p = plan(OnboardingPlanStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertPlanMutable(p)).isInstanceOf(ApiException.class);
    }

    @Test
    void taskEditableWhenNotTerminal() {
        OnboardingTaskInstance t = new OnboardingTaskInstance();
        t.setStatus(OnboardingTaskStatus.IN_PROGRESS);
        assertThatCode(() -> policy.assertTaskEditable(t)).doesNotThrowAnyException();
    }

    @Test
    void terminalTaskNotEditable() {
        for (OnboardingTaskStatus terminal :
                new OnboardingTaskStatus[] {
                    OnboardingTaskStatus.COMPLETED,
                    OnboardingTaskStatus.SKIPPED,
                    OnboardingTaskStatus.FAILED
                }) {
            OnboardingTaskInstance t = new OnboardingTaskInstance();
            t.setStatus(terminal);
            assertThatThrownBy(() -> policy.assertTaskEditable(t)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void isPlanTerminalFlag() {
        assert policy.isPlanTerminal(OnboardingPlanStatus.COMPLETED);
        assert policy.isPlanTerminal(OnboardingPlanStatus.CANCELLED);
        assert !policy.isPlanTerminal(OnboardingPlanStatus.IN_PROGRESS);
    }

    private static OnboardingPlan plan(OnboardingPlanStatus status) {
        OnboardingPlan p = new OnboardingPlan();
        p.setStatus(status);
        return p;
    }
}
