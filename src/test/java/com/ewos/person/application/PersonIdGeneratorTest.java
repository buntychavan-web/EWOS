package com.ewos.person.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PersonIdGeneratorTest {

    @Test
    void formatPadsToNineDigits() {
        assertThat(PersonIdGenerator.format(1)).isEqualTo("P000000001");
        assertThat(PersonIdGenerator.format(42)).isEqualTo("P000000042");
        assertThat(PersonIdGenerator.format(1_234_567_890L)).isEqualTo("P1234567890");
    }
}
