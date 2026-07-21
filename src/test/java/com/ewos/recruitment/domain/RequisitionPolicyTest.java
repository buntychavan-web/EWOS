package com.ewos.recruitment.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class RequisitionPolicyTest {

    private final RequisitionPolicy policy = new RequisitionPolicy();

    @Test
    void draftEditable() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        assertThatCode(() -> policy.assertEditable(r)).doesNotThrowAnyException();
    }

    @Test
    void nonDraftNotEditable() {
        JobRequisition r = requisition(RequisitionStatus.PENDING_APPROVAL);
        assertThatThrownBy(() -> policy.assertEditable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void submittableRequiresJustification() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        r.setHeadcount(1);
        r.setJustification(null);
        assertThatThrownBy(() -> policy.assertSubmittable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Justification");
    }

    @Test
    void submittableRequiresHeadcountAtLeastOne() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        r.setHeadcount(0);
        r.setJustification("Because");
        assertThatThrownBy(() -> policy.assertSubmittable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Headcount");
    }

    @Test
    void submittableHappyPath() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        r.setHeadcount(2);
        r.setJustification("Growth");
        assertThatCode(() -> policy.assertSubmittable(r)).doesNotThrowAnyException();
    }

    @Test
    void onlyPendingApprovalDecidable() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        assertThatThrownBy(() -> policy.assertDecidable(r)).isInstanceOf(ApiException.class);
        r.setStatus(RequisitionStatus.PENDING_APPROVAL);
        assertThatCode(() -> policy.assertDecidable(r)).doesNotThrowAnyException();
    }

    @Test
    void onlyApprovedOpenable() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        assertThatThrownBy(() -> policy.assertOpenable(r)).isInstanceOf(ApiException.class);
        r.setStatus(RequisitionStatus.APPROVED);
        assertThatCode(() -> policy.assertOpenable(r)).doesNotThrowAnyException();
    }

    @Test
    void holdRequiresOpen() {
        JobRequisition r = requisition(RequisitionStatus.APPROVED);
        assertThatThrownBy(() -> policy.assertHoldable(r)).isInstanceOf(ApiException.class);
        r.setStatus(RequisitionStatus.OPEN);
        assertThatCode(() -> policy.assertHoldable(r)).doesNotThrowAnyException();
    }

    @Test
    void resumeRequiresOnHold() {
        JobRequisition r = requisition(RequisitionStatus.OPEN);
        assertThatThrownBy(() -> policy.assertResumable(r)).isInstanceOf(ApiException.class);
        r.setStatus(RequisitionStatus.ON_HOLD);
        assertThatCode(() -> policy.assertResumable(r)).doesNotThrowAnyException();
    }

    @Test
    void fillableRequiresOpenOrOnHoldAndVacancy() {
        JobRequisition r = requisition(RequisitionStatus.APPROVED);
        r.setHeadcount(2);
        r.setFilledCount(0);
        assertThatThrownBy(() -> policy.assertFillable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("OPEN or ON_HOLD");

        r.setStatus(RequisitionStatus.OPEN);
        assertThatCode(() -> policy.assertFillable(r)).doesNotThrowAnyException();

        r.setFilledCount(2);
        assertThatThrownBy(() -> policy.assertFillable(r))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("vacancy");
    }

    @Test
    void terminalStatesNotCloseable() {
        for (RequisitionStatus s :
                new RequisitionStatus[] {
                    RequisitionStatus.CLOSED,
                    RequisitionStatus.CANCELLED,
                    RequisitionStatus.REJECTED,
                    RequisitionStatus.FILLED
                }) {
            JobRequisition r = requisition(s);
            assertThatThrownBy(() -> policy.assertCloseable(r)).isInstanceOf(ApiException.class);
        }
    }

    @Test
    void cancellableFromPendingOrOpenButNotTerminal() {
        JobRequisition r = requisition(RequisitionStatus.PENDING_APPROVAL);
        assertThatCode(() -> policy.assertCancellable(r)).doesNotThrowAnyException();
        r.setStatus(RequisitionStatus.OPEN);
        assertThatCode(() -> policy.assertCancellable(r)).doesNotThrowAnyException();
        r.setStatus(RequisitionStatus.FILLED);
        assertThatThrownBy(() -> policy.assertCancellable(r)).isInstanceOf(ApiException.class);
    }

    private static JobRequisition requisition(RequisitionStatus status) {
        JobRequisition r = new JobRequisition();
        r.setStatus(status);
        r.setHeadcount(1);
        r.setFilledCount(0);
        return r;
    }
}
