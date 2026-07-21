package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class PayrollPolicyTest {

    private final PayrollPolicy policy = new PayrollPolicy();

    @Test
    void editableOnlyOnOpenPeriod() {
        PayrollPeriod p = period(PayrollPeriodStatus.LOCKED);
        assertThatThrownBy(() -> policy.assertEditable(p)).isInstanceOf(ApiException.class);

        p.setStatus(PayrollPeriodStatus.OPEN);
        assertThatCode(() -> policy.assertEditable(p)).doesNotThrowAnyException();
    }

    @Test
    void lockableOnlyFromOpen() {
        PayrollPeriod p = period(PayrollPeriodStatus.CLOSED);
        assertThatThrownBy(() -> policy.assertLockable(p)).isInstanceOf(ApiException.class);
    }

    @Test
    void closableOnlyFromLocked() {
        PayrollPeriod p = period(PayrollPeriodStatus.OPEN);
        assertThatThrownBy(() -> policy.assertClosable(p)).isInstanceOf(ApiException.class);
        p.setStatus(PayrollPeriodStatus.LOCKED);
        assertThatCode(() -> policy.assertClosable(p)).doesNotThrowAnyException();
    }

    @Test
    void runsRequireLockedPeriod() {
        PayrollPeriod p = period(PayrollPeriodStatus.OPEN);
        assertThatThrownBy(() -> policy.assertRunnable(p)).isInstanceOf(ApiException.class);
        p.setStatus(PayrollPeriodStatus.LOCKED);
        assertThatCode(() -> policy.assertRunnable(p)).doesNotThrowAnyException();
        p.setStatus(PayrollPeriodStatus.CLOSED);
        assertThatThrownBy(() -> policy.assertRunnable(p)).isInstanceOf(ApiException.class);
    }

    @Test
    void startableOnlyFromPending() {
        PayrollRun r = run(PayrollRunStatus.PROCESSING);
        assertThatThrownBy(() -> policy.assertStartable(r)).isInstanceOf(ApiException.class);
        r.setStatus(PayrollRunStatus.PENDING);
        assertThatCode(() -> policy.assertStartable(r)).doesNotThrowAnyException();
    }

    @Test
    void finalizableOnlyFromCompleted() {
        PayrollRun r = run(PayrollRunStatus.PROCESSING);
        assertThatThrownBy(() -> policy.assertFinalizable(r)).isInstanceOf(ApiException.class);
        r.setStatus(PayrollRunStatus.COMPLETED);
        assertThatCode(() -> policy.assertFinalizable(r)).doesNotThrowAnyException();
    }

    @Test
    void terminalRunsCannotBecomeFailed() {
        PayrollRun r = run(PayrollRunStatus.FINALIZED);
        assertThatThrownBy(() -> policy.assertFailable(r)).isInstanceOf(ApiException.class);
        r.setStatus(PayrollRunStatus.FAILED);
        assertThatThrownBy(() -> policy.assertFailable(r)).isInstanceOf(ApiException.class);
        r.setStatus(PayrollRunStatus.PROCESSING);
        assertThatCode(() -> policy.assertFailable(r)).doesNotThrowAnyException();
    }

    private static PayrollPeriod period(PayrollPeriodStatus status) {
        PayrollPeriod p = new PayrollPeriod();
        p.setStatus(status);
        return p;
    }

    private static PayrollRun run(PayrollRunStatus status) {
        PayrollRun r = new PayrollRun();
        r.setStatus(status);
        return r;
    }
}
