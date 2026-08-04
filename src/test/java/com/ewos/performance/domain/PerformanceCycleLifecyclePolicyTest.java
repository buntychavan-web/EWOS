package com.ewos.performance.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PerformanceCycleLifecyclePolicyTest {

    private final PerformanceCycleLifecyclePolicy policy = new PerformanceCycleLifecyclePolicy();

    @Test
    void allowsEachStepOfTheForwardChain() {
        assertThatCode(
                        () -> {
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.DRAFT, PerformanceCycleStatus.OPEN);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.OPEN,
                                    PerformanceCycleStatus.SELF_REVIEW);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.SELF_REVIEW,
                                    PerformanceCycleStatus.MANAGER_REVIEW);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.MANAGER_REVIEW,
                                    PerformanceCycleStatus.REVIEWER_REVIEW);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.REVIEWER_REVIEW,
                                    PerformanceCycleStatus.CALIBRATION);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.CALIBRATION,
                                    PerformanceCycleStatus.HR_REVIEW);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.HR_REVIEW,
                                    PerformanceCycleStatus.FINAL_APPROVAL);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.FINAL_APPROVAL,
                                    PerformanceCycleStatus.RELEASED);
                            policy.assertValidTransition(
                                    PerformanceCycleStatus.RELEASED, PerformanceCycleStatus.CLOSED);
                        })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsSkippingAheadInTheChain() {
        assertThatThrownBy(
                        () ->
                                policy.assertValidTransition(
                                        PerformanceCycleStatus.DRAFT,
                                        PerformanceCycleStatus.CLOSED))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsMovingBackwards() {
        assertThatThrownBy(
                        () ->
                                policy.assertValidTransition(
                                        PerformanceCycleStatus.MANAGER_REVIEW,
                                        PerformanceCycleStatus.SELF_REVIEW))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void allowsCancellingFromAnyNonTerminalStatus() {
        for (PerformanceCycleStatus status : PerformanceCycleStatus.values()) {
            if (policy.isTerminal(status)) {
                continue;
            }
            assertThatCode(
                            () ->
                                    policy.assertValidTransition(
                                            status, PerformanceCycleStatus.CANCELLED))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsReopeningAClosedCycle() {
        assertThatThrownBy(
                        () ->
                                policy.assertValidTransition(
                                        PerformanceCycleStatus.CLOSED, PerformanceCycleStatus.OPEN))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsReopeningACancelledCycle() {
        assertThatThrownBy(
                        () ->
                                policy.assertValidTransition(
                                        PerformanceCycleStatus.CANCELLED,
                                        PerformanceCycleStatus.DRAFT))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void rejectsTransitioningToTheSameStatus() {
        assertThatThrownBy(
                        () ->
                                policy.assertValidTransition(
                                        PerformanceCycleStatus.OPEN, PerformanceCycleStatus.OPEN))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
