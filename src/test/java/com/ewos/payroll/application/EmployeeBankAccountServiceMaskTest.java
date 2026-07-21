package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmployeeBankAccountServiceMaskTest {

    @Test
    void masksAllButLastFour() {
        assertThat(EmployeeBankAccountService.mask("1234567890")).isEqualTo("******7890");
    }

    @Test
    void shortNumberIsFullyMasked() {
        assertThat(EmployeeBankAccountService.mask("1234")).isEqualTo("****");
        assertThat(EmployeeBankAccountService.mask("12")).isEqualTo("**");
    }

    @Test
    void nullReturnsNull() {
        assertThat(EmployeeBankAccountService.mask(null)).isNull();
    }

    @Test
    void trimsWhitespace() {
        assertThat(EmployeeBankAccountService.mask("  1234567890  ")).isEqualTo("******7890");
    }
}
