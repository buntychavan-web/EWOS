package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.application.EsiCalculationService.EsiResult;
import com.ewos.payroll.domain.EmployeeEsiEnrollment;
import com.ewos.payroll.domain.EsiConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EsiCalculationServiceTest {

    private final EsiCalculationService service = new EsiCalculationService();

    private static EsiConfiguration standardConfig() {
        EsiConfiguration c = new EsiConfiguration();
        c.setWageThreshold(new BigDecimal("21000"));
        c.setEmployeeRatePct(new BigDecimal("0.75"));
        c.setEmployerRatePct(new BigDecimal("3.25"));
        return c;
    }

    @Test
    void contributionPeriodStartsAtAprilOrOctober() {
        assertThat(service.contributionPeriodStart(LocalDate.of(2026, 4, 10)))
                .isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(service.contributionPeriodStart(LocalDate.of(2026, 9, 5)))
                .isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(service.contributionPeriodStart(LocalDate.of(2026, 10, 20)))
                .isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(service.contributionPeriodStart(LocalDate.of(2026, 1, 15)))
                .isEqualTo(LocalDate.of(2025, 10, 1));
    }

    @Test
    void newEnrollmentBelowThresholdIsEligibleAndComputesContributions() {
        EsiResult r =
                service.calculate(
                        standardConfig(),
                        Optional.empty(),
                        LocalDate.of(2026, 4, 5),
                        LocalDate.of(2020, 1, 1),
                        new BigDecimal("20000"),
                        new Employee(),
                        UUID.randomUUID(),
                        UUID.randomUUID());

        assertThat(r.eligible()).isTrue();
        assertThat(r.enrollmentIsNew()).isTrue();
        assertThat(r.employeeContribution()).isEqualByComparingTo("150.00");
        assertThat(r.employerContribution()).isEqualByComparingTo("650.00");
        assertThat(r.enrollment().getContributionPeriodStart()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(r.enrollment().getContributionPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    void newEnrollmentAboveThresholdIsNotEligible() {
        EsiResult r =
                service.calculate(
                        standardConfig(),
                        Optional.empty(),
                        LocalDate.of(2026, 4, 5),
                        LocalDate.of(2020, 1, 1),
                        new BigDecimal("25000"),
                        new Employee(),
                        UUID.randomUUID(),
                        UUID.randomUUID());

        assertThat(r.eligible()).isFalse();
        assertThat(r.employeeContribution()).isEqualByComparingTo("0");
        assertThat(r.employerContribution()).isEqualByComparingTo("0");
        assertThat(r.enrollment().isEligibleAtPeriodStart()).isFalse();
    }

    @Test
    void existingEligibleEnrollmentStaysEligibleForRestOfPeriodEvenIfWageRisesAboveThreshold() {
        EmployeeEsiEnrollment existing = new EmployeeEsiEnrollment();
        existing.setContributionPeriodStart(LocalDate.of(2026, 4, 1));
        existing.setContributionPeriodEnd(LocalDate.of(2026, 9, 30));
        existing.setEligibleAtPeriodStart(true);
        existing.setWageAtPeriodStart(new BigDecimal("18000.00"));

        EsiResult r =
                service.calculate(
                        standardConfig(),
                        Optional.of(existing),
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2020, 1, 1),
                        new BigDecimal("30000"),
                        new Employee(),
                        UUID.randomUUID(),
                        UUID.randomUUID());

        assertThat(r.eligible()).isTrue();
        assertThat(r.enrollmentIsNew()).isFalse();
        assertThat(r.employeeContribution()).isEqualByComparingTo("225.00");
        assertThat(r.employerContribution()).isEqualByComparingTo("975.00");
    }

    @Test
    void existingIneligibleEnrollmentStaysIneligibleForRestOfPeriod() {
        EmployeeEsiEnrollment existing = new EmployeeEsiEnrollment();
        existing.setContributionPeriodStart(LocalDate.of(2026, 4, 1));
        existing.setContributionPeriodEnd(LocalDate.of(2026, 9, 30));
        existing.setEligibleAtPeriodStart(false);
        existing.setWageAtPeriodStart(new BigDecimal("25000.00"));

        EsiResult r =
                service.calculate(
                        standardConfig(),
                        Optional.of(existing),
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2020, 1, 1),
                        new BigDecimal("15000"),
                        new Employee(),
                        UUID.randomUUID(),
                        UUID.randomUUID());

        assertThat(r.eligible()).isFalse();
        assertThat(r.employeeContribution()).isEqualByComparingTo("0");
    }
}
