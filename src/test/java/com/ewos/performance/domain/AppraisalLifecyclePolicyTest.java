package com.ewos.performance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AppraisalLifecyclePolicyTest {

    private final AppraisalLifecyclePolicy policy = new AppraisalLifecyclePolicy();

    @Test
    void assertOpenable_rejectsClosedCycle() {
        PerformanceCycle c = newCycle();
        c.setStatus(PerformanceCycleStatus.CLOSED);
        AppraisalTemplate t = newTemplate();
        assertThatThrownBy(() -> policy.assertOpenable(c, t))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cycle status");
    }

    @Test
    void assertOpenable_rejectsInactiveTemplate() {
        PerformanceCycle c = newCycle();
        AppraisalTemplate t = newTemplate();
        t.setActive(false);
        assertThatThrownBy(() -> policy.assertOpenable(c, t))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Template is inactive");
    }

    @Test
    void assertSelfSubmittable_rejectsWrongStatus() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.PENDING_MANAGER);
        assertThatThrownBy(() -> policy.assertSelfSubmittable(a)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertSelfSubmittable_acceptsPendingSelf() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.PENDING_SELF);
        assertThatCode(() -> policy.assertSelfSubmittable(a)).doesNotThrowAnyException();
    }

    @Test
    void assertManagerSubmittable_rejectsWrongStatus() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.PENDING_SELF);
        assertThatThrownBy(() -> policy.assertManagerSubmittable(a))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assertReviewerSubmittable_rejectsWrongStatus() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.CALIBRATION);
        assertThatThrownBy(() -> policy.assertReviewerSubmittable(a))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void assertCalibratable_rejectsPreCalibrationStatus() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.PENDING_SELF);
        assertThatThrownBy(() -> policy.assertCalibratable(a)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertSubmittableForApproval_requiresARating() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.CALIBRATION);
        assertThatThrownBy(() -> policy.assertSubmittableForApproval(a))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("reviewer or calibrated rating");
    }

    @Test
    void assertSubmittableForApproval_acceptsReviewerRating() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.CALIBRATION);
        a.setReviewerRating(new BigDecimal("4.0"));
        assertThatCode(() -> policy.assertSubmittableForApproval(a)).doesNotThrowAnyException();
    }

    @Test
    void assertFinalisable_rejectsWrongStatus() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.CALIBRATION);
        assertThatThrownBy(() -> policy.assertFinalisable(a)).isInstanceOf(ApiException.class);
    }

    @Test
    void assertNotTerminal_flagsFinalised() {
        Appraisal a = new Appraisal();
        a.setStatus(AppraisalStatus.FINALISED);
        assertThatThrownBy(() -> policy.assertNotTerminal(a)).isInstanceOf(ApiException.class);
    }

    @Test
    void isTerminal_matchesExpectedStatuses() {
        assertThat(policy.isTerminal(AppraisalStatus.FINALISED)).isTrue();
        assertThat(policy.isTerminal(AppraisalStatus.CANCELLED)).isTrue();
        assertThat(policy.isTerminal(AppraisalStatus.PENDING_SELF)).isFalse();
        assertThat(policy.isTerminal(AppraisalStatus.PENDING_APPROVAL)).isFalse();
    }

    @Test
    void assertRatingInScale_rejectsBelowMin() {
        AppraisalTemplate t = newTemplate();
        assertThatThrownBy(() -> policy.assertRatingInScale(new BigDecimal("0"), t))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("outside scale");
    }

    @Test
    void assertRatingInScale_rejectsAboveMax() {
        AppraisalTemplate t = newTemplate();
        assertThatThrownBy(() -> policy.assertRatingInScale(new BigDecimal("6"), t))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("outside scale");
    }

    @Test
    void assertRatingInScale_allowsBoundaryValues() {
        AppraisalTemplate t = newTemplate();
        assertThatCode(() -> policy.assertRatingInScale(new BigDecimal("1"), t))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertRatingInScale(new BigDecimal("5"), t))
                .doesNotThrowAnyException();
    }

    @Test
    void assertRatingInScale_ignoresNull() {
        AppraisalTemplate t = newTemplate();
        assertThatCode(() -> policy.assertRatingInScale(null, t)).doesNotThrowAnyException();
    }

    private PerformanceCycle newCycle() {
        PerformanceCycle c = new PerformanceCycle();
        c.setStatus(PerformanceCycleStatus.OPEN);
        return c;
    }

    private AppraisalTemplate newTemplate() {
        AppraisalTemplate t = new AppraisalTemplate();
        t.setRatingScaleMin(1);
        t.setRatingScaleMax(5);
        t.setActive(true);
        return t;
    }
}
