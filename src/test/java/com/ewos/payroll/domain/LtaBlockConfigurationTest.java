package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Sprint 24K §8.1 — the block-year math a new government block boundary never has to touch. */
class LtaBlockConfigurationTest {

    private static LtaBlockConfiguration config(int anchorYear, int durationYears) {
        LtaBlockConfiguration c = new LtaBlockConfiguration();
        c.setAnchorBlockStartYear(anchorYear);
        c.setBlockDurationYears(durationYears);
        return c;
    }

    @Test
    void firstCalendarYearOfTheAnchorBlockMapsToItself() {
        LtaBlockConfiguration c = config(2022, 4);
        assertThat(c.blockStartYearFor(2022)).isEqualTo(2022);
        assertThat(c.blockEndYearFor(2022)).isEqualTo(2025);
    }

    @Test
    void lastCalendarYearOfTheAnchorBlockStillMapsToTheAnchorBlock() {
        LtaBlockConfiguration c = config(2022, 4);
        assertThat(c.blockStartYearFor(2025)).isEqualTo(2022);
        assertThat(c.blockEndYearFor(2025)).isEqualTo(2025);
    }

    @Test
    void yearJustAfterTheAnchorBlockRollsIntoTheNextBlockAutomatically() {
        LtaBlockConfiguration c = config(2022, 4);
        assertThat(c.blockStartYearFor(2026)).isEqualTo(2026);
        assertThat(c.blockEndYearFor(2026)).isEqualTo(2029);
    }

    @Test
    void yearsBeforeTheAnchorResolveToThePriorBlockRatherThanUnderflowing() {
        LtaBlockConfiguration c = config(2022, 4);
        assertThat(c.blockStartYearFor(2021)).isEqualTo(2018);
        assertThat(c.blockStartYearFor(2018)).isEqualTo(2018);
    }
}
