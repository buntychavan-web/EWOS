package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayslipLineExplainerTest {

    @Test
    void percentOfBasicExplainsTheAppliedPercentage() {
        String explanation =
                PayslipLineExplainer.explain(
                        "HRA",
                        PayComponentKind.EARNING,
                        PayComponentCalculationType.PERCENT_OF_BASIC,
                        new BigDecimal("40.00"));

        assertThat(explanation).contains("40").contains("basic salary");
    }

    @Test
    void statutoryPfHasAFixedRuleBasedExplanation() {
        String explanation =
                PayslipLineExplainer.explain(
                        "PF",
                        PayComponentKind.DEDUCTION,
                        PayComponentCalculationType.STATUTORY_PF,
                        null);

        assertThat(explanation).contains("Provident Fund");
    }

    @Test
    void fixedDeductionNamesTheComponent() {
        String explanation =
                PayslipLineExplainer.explain(
                        "Loan EMI",
                        PayComponentKind.DEDUCTION,
                        PayComponentCalculationType.FIXED,
                        null);

        assertThat(explanation).contains("deduction").contains("Loan EMI");
    }

    @Test
    void fixedEarningNamesTheComponent() {
        String explanation =
                PayslipLineExplainer.explain(
                        "Travel Reimbursement",
                        PayComponentKind.EARNING,
                        PayComponentCalculationType.FIXED,
                        null);

        assertThat(explanation).contains("amount").contains("Travel Reimbursement");
    }
}
