package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinalSettlementLifecycleTest {

    @Test
    void defaultStatusIsDraft() {
        FinalSettlement s = new FinalSettlement();
        assertThat(s.getStatus()).isEqualTo(FinalSettlementStatus.DRAFT);
    }

    @Test
    void amountsDefaultToZero() {
        FinalSettlement s = new FinalSettlement();
        s.setTerminationDate(LocalDate.of(2026, 6, 30));
        s.setLastWorkingDate(LocalDate.of(2026, 6, 30));
        assertThat(s.getEncashmentAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getGratuityAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getNoticePayReceivable()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(s.getNoticePayRecovery()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void currencyDefaultsToUSD() {
        FinalSettlement s = new FinalSettlement();
        assertThat(s.getCurrency()).isEqualTo("USD");
    }
}
