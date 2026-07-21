package com.ewos.ats.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNormalizerTest {

    @Test
    void nullInReturnsNull() {
        assertThat(PhoneNormalizer.digitsOnly(null)).isNull();
    }

    @Test
    void blankInReturnsNull() {
        assertThat(PhoneNormalizer.digitsOnly("  ")).isNull();
    }

    @Test
    void stripsAllNonDigits() {
        assertThat(PhoneNormalizer.digitsOnly("+91 (98) 76543-210")).isEqualTo("919876543210");
    }

    @Test
    void keepsPurelyNumeric() {
        assertThat(PhoneNormalizer.digitsOnly("18001234567")).isEqualTo("18001234567");
    }
}
