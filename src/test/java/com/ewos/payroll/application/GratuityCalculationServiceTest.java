package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.application.GratuityCalculationService.GratuityResult;
import com.ewos.payroll.application.GratuityCalculationService.WaiverReason;
import com.ewos.payroll.domain.GratuityConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GratuityCalculationServiceTest {

    private final GratuityCalculationService service = new GratuityCalculationService();

    private static GratuityConfiguration standardConfig() {
        GratuityConfiguration c = new GratuityConfiguration();
        c.setStatutoryCeiling(new BigDecimal("2000000"));
        c.setRateNumerator(15);
        c.setRateDenominator(26);
        c.setMinYearsEligibility(new BigDecimal("5"));
        return c;
    }

    @Test
    void exactTenYearsOfServiceUsesFullFormula() {
        GratuityResult r =
                service.calculate(
                        standardConfig(),
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2025, 1, 1),
                        new BigDecimal("50000"),
                        null);

        assertThat(r.eligible()).isTrue();
        assertThat(r.completedYears()).isEqualTo(10);
        assertThat(r.amount()).isEqualByComparingTo("288461.54");
    }

    @Test
    void partialYearOverSixMonthsRoundsUpToNextFullYear() {
        GratuityResult r =
                service.calculate(
                        standardConfig(),
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2022, 8, 15),
                        new BigDecimal("50000"),
                        null);

        assertThat(r.completedYears()).isEqualTo(8);
    }

    @Test
    void partialYearOfExactlySixMonthsDoesNotRoundUp() {
        GratuityResult r =
                service.calculate(
                        standardConfig(),
                        LocalDate.of(2015, 1, 1),
                        LocalDate.of(2022, 7, 1),
                        new BigDecimal("50000"),
                        null);

        assertThat(r.completedYears()).isEqualTo(7);
    }

    @Test
    void lessThanFiveYearsIsNotEligibleWithoutWaiver() {
        GratuityResult r =
                service.calculate(
                        standardConfig(),
                        LocalDate.of(2022, 1, 1),
                        LocalDate.of(2024, 6, 1),
                        new BigDecimal("50000"),
                        null);

        assertThat(r.eligible()).isFalse();
        assertThat(r.amount()).isEqualByComparingTo("0");
    }

    @Test
    void deathWaiverGrantsEligibilityBelowFiveYears() {
        GratuityResult r =
                service.calculate(
                        standardConfig(),
                        LocalDate.of(2022, 1, 1),
                        LocalDate.of(2024, 6, 1),
                        new BigDecimal("50000"),
                        WaiverReason.DEATH);

        assertThat(r.eligible()).isTrue();
        assertThat(r.completedYears()).isEqualTo(2);
        assertThat(r.amount()).isEqualByComparingTo("57692.31");
    }

    @Test
    void amountIsCappedAtTheStatutoryCeiling() {
        GratuityResult r =
                service.calculate(
                        standardConfig(),
                        LocalDate.of(1994, 1, 1),
                        LocalDate.of(2024, 1, 1),
                        new BigDecimal("500000"),
                        null);

        assertThat(r.completedYears()).isEqualTo(30);
        assertThat(r.amount()).isEqualByComparingTo("2000000.00");
    }
}
