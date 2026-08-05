package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpreadsheetFormulaGuardTest {

    @Test
    void prefixesValuesStartingWithEquals() {
        assertThat(SpreadsheetFormulaGuard.neutralize("=cmd|'/c calc'!A1")).startsWith("'=");
    }

    @Test
    void prefixesValuesStartingWithPlusMinusOrAt() {
        assertThat(SpreadsheetFormulaGuard.neutralize("+1+1")).startsWith("'+");
        assertThat(SpreadsheetFormulaGuard.neutralize("-1+1")).startsWith("'-");
        assertThat(SpreadsheetFormulaGuard.neutralize("@SUM(1,1)")).startsWith("'@");
    }

    @Test
    void leavesOrdinaryValuesUnchanged() {
        assertThat(SpreadsheetFormulaGuard.neutralize("{\"amount\":100}"))
                .isEqualTo("{\"amount\":100}");
        assertThat(SpreadsheetFormulaGuard.neutralize("PAYROLL_RUN:abc"))
                .isEqualTo("PAYROLL_RUN:abc");
    }

    @Test
    void handlesNullAndEmpty() {
        assertThat(SpreadsheetFormulaGuard.neutralize(null)).isNull();
        assertThat(SpreadsheetFormulaGuard.neutralize("")).isEmpty();
    }
}
