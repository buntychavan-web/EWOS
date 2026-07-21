package com.ewos.onboarding.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OnboardingTaskInstanceTest {

    @Test
    void terminalWhenCompletedSkippedOrFailed() {
        for (OnboardingTaskStatus t :
                new OnboardingTaskStatus[] {
                    OnboardingTaskStatus.COMPLETED,
                    OnboardingTaskStatus.SKIPPED,
                    OnboardingTaskStatus.FAILED
                }) {
            OnboardingTaskInstance task = new OnboardingTaskInstance();
            task.setStatus(t);
            assertThat(task.isTerminal()).isTrue();
        }
    }

    @Test
    void nonTerminalOtherwise() {
        for (OnboardingTaskStatus t :
                new OnboardingTaskStatus[] {
                    OnboardingTaskStatus.PENDING,
                    OnboardingTaskStatus.IN_PROGRESS,
                    OnboardingTaskStatus.WAITING_ON_EMPLOYEE,
                    OnboardingTaskStatus.WAITING_ON_COMPANY
                }) {
            OnboardingTaskInstance task = new OnboardingTaskInstance();
            task.setStatus(t);
            assertThat(task.isTerminal()).isFalse();
        }
    }
}
