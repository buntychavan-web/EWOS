package com.ewos.succession.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SuccessionEnumsTest {

    @Test
    void talentTierValues() {
        assertThat(TalentTier.values()).hasSize(5);
        assertThat(TalentTier.valueOf("HIGH_POTENTIAL")).isEqualTo(TalentTier.HIGH_POTENTIAL);
    }

    @Test
    void readinessLevelValues() {
        assertThat(ReadinessLevel.values()).hasSize(4);
        assertThat(ReadinessLevel.valueOf("READY_NOW")).isEqualTo(ReadinessLevel.READY_NOW);
    }
}
