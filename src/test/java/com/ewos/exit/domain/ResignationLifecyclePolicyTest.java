package com.ewos.exit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class ResignationLifecyclePolicyTest {

    private final ResignationLifecyclePolicy policy = new ResignationLifecyclePolicy();

    @Test
    void submittedToAcceptedAllowed() {
        policy.assertTransition(ResignationStatus.SUBMITTED, ResignationStatus.ACCEPTED);
    }

    @Test
    void submittedToWithdrawnAllowed() {
        policy.assertTransition(ResignationStatus.SUBMITTED, ResignationStatus.WITHDRAWN);
    }

    @Test
    void submittedToExitedBlocked() {
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ResignationStatus.SUBMITTED, ResignationStatus.EXITED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void acceptedToNoticeAllowed() {
        policy.assertTransition(ResignationStatus.ACCEPTED, ResignationStatus.IN_NOTICE);
    }

    @Test
    void acceptedToExitedAllowed() {
        policy.assertTransition(ResignationStatus.ACCEPTED, ResignationStatus.EXITED);
    }

    @Test
    void inNoticeToExitedAllowed() {
        policy.assertTransition(ResignationStatus.IN_NOTICE, ResignationStatus.EXITED);
    }

    @Test
    void inNoticeToWithdrawnBlocked() {
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ResignationStatus.IN_NOTICE, ResignationStatus.WITHDRAWN))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void exitedIsTerminal() {
        assertThat(policy.isTerminal(ResignationStatus.EXITED)).isTrue();
        assertThat(policy.isOpen(ResignationStatus.EXITED)).isFalse();
    }

    @Test
    void withdrawnIsTerminal() {
        assertThat(policy.isTerminal(ResignationStatus.WITHDRAWN)).isTrue();
    }

    @Test
    void cancelledIsTerminal() {
        assertThat(policy.isTerminal(ResignationStatus.CANCELLED)).isTrue();
    }

    @Test
    void submittedIsOpen() {
        assertThat(policy.isOpen(ResignationStatus.SUBMITTED)).isTrue();
    }

    @Test
    void inNoticeIsOpen() {
        assertThat(policy.isOpen(ResignationStatus.IN_NOTICE)).isTrue();
    }

    @Test
    void exitedIsTerminalCannotTransitionFurther() {
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ResignationStatus.EXITED, ResignationStatus.ACCEPTED))
                .isInstanceOf(ApiException.class);
    }
}
