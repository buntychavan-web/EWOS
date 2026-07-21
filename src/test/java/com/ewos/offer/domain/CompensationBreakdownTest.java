package com.ewos.offer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompensationBreakdownTest {

    @Test
    void sumsAllComponents() {
        CompensationBreakdown c =
                new CompensationBreakdown(
                        "INR",
                        new BigDecimal("1000"),
                        new BigDecimal("100"),
                        new BigDecimal("10"),
                        new BigDecimal("5"),
                        new BigDecimal("2"));
        assertThat(c.totalCtc()).isEqualByComparingTo("1117");
    }

    @Test
    void treatsNullsAsZero() {
        CompensationBreakdown c =
                new CompensationBreakdown("INR", new BigDecimal("500"), null, null, null, null);
        assertThat(c.totalCtc()).isEqualByComparingTo("500");
    }
}
