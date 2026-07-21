package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import org.junit.jupiter.api.Test;

class PayrollPolicyFreezeTest {

    private final PayrollPolicy policy = new PayrollPolicy();

    @Test
    void freezableOnlyFromFinalized() {
        PayrollRun r = new PayrollRun();
        r.setStatus(PayrollRunStatus.COMPLETED);
        assertThatThrownBy(() -> policy.assertFreezable(r)).isInstanceOf(ApiException.class);
        r.setStatus(PayrollRunStatus.FINALIZED);
        assertThatCode(() -> policy.assertFreezable(r)).doesNotThrowAnyException();
        r.setStatus(PayrollRunStatus.FROZEN);
        assertThatThrownBy(() -> policy.assertFreezable(r)).isInstanceOf(ApiException.class);
    }

    @Test
    void frozenRunCannotBecomeFailed() {
        PayrollRun r = new PayrollRun();
        r.setStatus(PayrollRunStatus.FROZEN);
        assertThatThrownBy(() -> policy.assertFailable(r)).isInstanceOf(ApiException.class);
    }
}
