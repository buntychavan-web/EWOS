package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.application.PfCalculationService.PfResult;
import com.ewos.payroll.domain.PfConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PfCalculationServiceTest {

    private final PfCalculationService service = new PfCalculationService();

    private static PfConfiguration standardConfig() {
        PfConfiguration c = new PfConfiguration();
        c.setWageCeiling(new BigDecimal("15000"));
        c.setEpsWageCeiling(new BigDecimal("15000"));
        c.setEmployeeRatePct(new BigDecimal("12"));
        c.setEmployerPfRatePct(new BigDecimal("12"));
        c.setEpsRatePct(new BigDecimal("8.33"));
        return c;
    }

    @Test
    void wageAboveCeilingIsCappedForEmployeeAndEmployerButVpfUsesGrossWage() {
        PfResult r =
                service.calculate(
                        standardConfig(), new BigDecimal("20000"), false, false, BigDecimal.ZERO);

        assertThat(r.pfWageUsed()).isEqualByComparingTo("15000.00");
        assertThat(r.employeeContribution()).isEqualByComparingTo("1800.00");
        assertThat(r.epsContribution()).isEqualByComparingTo("1249.50");
        assertThat(r.employerPfContribution()).isEqualByComparingTo("550.50");
    }

    @Test
    void internationalWorkerHasNoWageCeiling() {
        PfResult r =
                service.calculate(
                        standardConfig(), new BigDecimal("20000"), true, false, BigDecimal.ZERO);

        assertThat(r.pfWageUsed()).isEqualByComparingTo("20000.00");
        assertThat(r.employeeContribution()).isEqualByComparingTo("2400.00");
        assertThat(r.epsContribution()).isEqualByComparingTo("1249.50");
        assertThat(r.employerPfContribution()).isEqualByComparingTo("1150.50");
    }

    @Test
    void vpfAddsOnTopOfEmployeePfWithoutItsOwnCeiling() {
        PfResult r =
                service.calculate(
                        standardConfig(),
                        new BigDecimal("10000"),
                        false,
                        true,
                        new BigDecimal("5"));

        assertThat(r.employeeContribution()).isEqualByComparingTo("1700.00");
        assertThat(r.epsContribution()).isEqualByComparingTo("833.00");
        assertThat(r.employerPfContribution()).isEqualByComparingTo("367.00");
    }

    @Test
    void zeroOrNullWageYieldsZeroResult() {
        assertThat(
                        service.calculate(
                                        standardConfig(),
                                        BigDecimal.ZERO,
                                        false,
                                        false,
                                        BigDecimal.ZERO)
                                .employeeContribution())
                .isEqualByComparingTo("0");
        assertThat(
                        service.calculate(standardConfig(), null, false, false, BigDecimal.ZERO)
                                .employeeContribution())
                .isEqualByComparingTo("0");
        assertThat(
                        service.calculate(
                                        null,
                                        new BigDecimal("20000"),
                                        false,
                                        false,
                                        BigDecimal.ZERO)
                                .employeeContribution())
                .isEqualByComparingTo("0");
    }
}
