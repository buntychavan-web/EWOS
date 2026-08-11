package com.ewos.reimbursement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class ReimbursementClaimLifecyclePolicyTest {

    private final ReimbursementClaimLifecyclePolicy policy =
            new ReimbursementClaimLifecyclePolicy();

    @Test
    void draftToSubmittedAllowed() {
        policy.assertTransition(ReimbursementClaimStatus.DRAFT, ReimbursementClaimStatus.SUBMITTED);
    }

    @Test
    void draftToCancelledAllowed() {
        policy.assertTransition(ReimbursementClaimStatus.DRAFT, ReimbursementClaimStatus.CANCELLED);
    }

    @Test
    void draftToManagerApprovedBlocked() {
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ReimbursementClaimStatus.DRAFT,
                                        ReimbursementClaimStatus.MANAGER_APPROVED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void submittedToManagerApprovedAllowed() {
        policy.assertTransition(
                ReimbursementClaimStatus.SUBMITTED, ReimbursementClaimStatus.MANAGER_APPROVED);
    }

    @Test
    void submittedToManagerRejectedAllowed() {
        policy.assertTransition(
                ReimbursementClaimStatus.SUBMITTED, ReimbursementClaimStatus.MANAGER_REJECTED);
    }

    @Test
    void submittedToCancelledAllowed() {
        policy.assertTransition(
                ReimbursementClaimStatus.SUBMITTED, ReimbursementClaimStatus.CANCELLED);
    }

    @Test
    void submittedToFinanceApprovedBlocked() {
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ReimbursementClaimStatus.SUBMITTED,
                                        ReimbursementClaimStatus.FINANCE_APPROVED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void managerApprovedToFinanceApprovedAllowed() {
        policy.assertTransition(
                ReimbursementClaimStatus.MANAGER_APPROVED,
                ReimbursementClaimStatus.FINANCE_APPROVED);
    }

    @Test
    void managerApprovedToFinanceRejectedAllowed() {
        policy.assertTransition(
                ReimbursementClaimStatus.MANAGER_APPROVED,
                ReimbursementClaimStatus.FINANCE_REJECTED);
    }

    @Test
    void managerApprovedToCancelledBlocked() {
        // An employee can no longer unilaterally withdraw a claim once a manager has decided it.
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ReimbursementClaimStatus.MANAGER_APPROVED,
                                        ReimbursementClaimStatus.CANCELLED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void managerRejectedIsTerminal() {
        assertThat(policy.isTerminal(ReimbursementClaimStatus.MANAGER_REJECTED)).isTrue();
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ReimbursementClaimStatus.MANAGER_REJECTED,
                                        ReimbursementClaimStatus.SUBMITTED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void financeApprovedIsTerminal() {
        // No PAID state exists in Sprint 2 — FINANCE_APPROVED has no outgoing transitions yet.
        assertThat(policy.isTerminal(ReimbursementClaimStatus.FINANCE_APPROVED)).isTrue();
    }

    @Test
    void financeRejectedIsTerminal() {
        assertThat(policy.isTerminal(ReimbursementClaimStatus.FINANCE_REJECTED)).isTrue();
    }

    @Test
    void cancelledIsTerminal() {
        assertThat(policy.isTerminal(ReimbursementClaimStatus.CANCELLED)).isTrue();
        assertThatThrownBy(
                        () ->
                                policy.assertTransition(
                                        ReimbursementClaimStatus.CANCELLED,
                                        ReimbursementClaimStatus.SUBMITTED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void draftAndSubmittedAreNotTerminal() {
        assertThat(policy.isTerminal(ReimbursementClaimStatus.DRAFT)).isFalse();
        assertThat(policy.isTerminal(ReimbursementClaimStatus.SUBMITTED)).isFalse();
        assertThat(policy.isTerminal(ReimbursementClaimStatus.MANAGER_APPROVED)).isFalse();
    }
}
