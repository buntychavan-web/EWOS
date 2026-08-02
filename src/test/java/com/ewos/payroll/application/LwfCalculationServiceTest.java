package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.application.LwfCalculationService.LwfResult;
import com.ewos.payroll.domain.LwfConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LwfCalculationServiceTest {

    private final LwfCalculationService service = new LwfCalculationService();

    private static LwfConfiguration config(String remittanceMonths) {
        LwfConfiguration c = new LwfConfiguration();
        c.setEmployeeContribution(new BigDecimal("20.00"));
        c.setEmployerContribution(new BigDecimal("40.00"));
        c.setRemittanceMonths(remittanceMonths);
        return c;
    }

    @Test
    void chargesEveryMonthWhenRemittanceMonthsIsUnconfigured() {
        LwfResult r = service.calculate(config(null), LocalDate.of(2026, 5, 1));
        assertThat(r.employeeContribution()).isEqualByComparingTo("20.00");
        assertThat(r.employerContribution()).isEqualByComparingTo("40.00");
    }

    @Test
    void chargesOnlyInConfiguredMonths() {
        LwfConfiguration c = config("6,12");

        assertThat(service.calculate(c, LocalDate.of(2026, 6, 15)).employeeContribution())
                .isEqualByComparingTo("20.00");
        assertThat(service.calculate(c, LocalDate.of(2026, 12, 15)).employeeContribution())
                .isEqualByComparingTo("20.00");
        assertThat(service.calculate(c, LocalDate.of(2026, 5, 15)).employeeContribution())
                .isEqualByComparingTo("0");
    }

    @Test
    void nullConfigYieldsZero() {
        LwfResult r = service.calculate(null, LocalDate.of(2026, 5, 1));
        assertThat(r.employeeContribution()).isEqualByComparingTo("0");
        assertThat(r.employerContribution()).isEqualByComparingTo("0");
    }
}
