package com.ewos.payroll.application;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.domain.EmployeeEsiEnrollment;
import com.ewos.payroll.domain.EsiConfiguration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * ESI math with contribution-period locking: eligibility (and the wage it was decided on) is fixed
 * once, at the later of the fixed half-year period's start or the employee's hire date, and holds
 * for the rest of that period regardless of later wage changes — per the ESI Act. Pure: it never
 * touches the database. Any new {@link EmployeeEsiEnrollment} it creates is returned for the caller
 * (PayrollRunService) to persist; existing enrollments passed in are reused unmodified.
 */
@Service
public class EsiCalculationService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final int H1_START_MONTH = 4;
    private static final int H2_START_MONTH = 10;

    public record EsiResult(
            BigDecimal employeeContribution,
            BigDecimal employerContribution,
            boolean eligible,
            EmployeeEsiEnrollment enrollment,
            boolean enrollmentIsNew) {

        static EsiResult notEligible(EmployeeEsiEnrollment enrollment, boolean isNew) {
            return new EsiResult(BigDecimal.ZERO, BigDecimal.ZERO, false, enrollment, isNew);
        }
    }

    /**
     * Start date of the fixed ESI contribution half-year (Apr&ndash;Sep or Oct&ndash;Mar)
     * containing {@code date}.
     */
    public LocalDate contributionPeriodStart(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        if (month >= H1_START_MONTH && month < H2_START_MONTH) {
            return LocalDate.of(year, H1_START_MONTH, 1);
        }
        return month >= H2_START_MONTH
                ? LocalDate.of(year, H2_START_MONTH, 1)
                : LocalDate.of(year - 1, H2_START_MONTH, 1);
    }

    public LocalDate contributionPeriodEnd(LocalDate periodStart) {
        return periodStart.plusMonths(6).minusDays(1);
    }

    public EsiResult calculate(
            EsiConfiguration config,
            Optional<EmployeeEsiEnrollment> existingEnrollment,
            LocalDate payPeriodDate,
            LocalDate hireDate,
            BigDecimal currentGrossWage,
            Employee employee,
            UUID tenantId,
            UUID companyId) {
        if (config == null || currentGrossWage == null) {
            return EsiResult.notEligible(existingEnrollment.orElse(null), false);
        }

        EmployeeEsiEnrollment enrollment;
        boolean isNew;
        if (existingEnrollment.isPresent()) {
            enrollment = existingEnrollment.get();
            isNew = false;
        } else {
            LocalDate periodStart = contributionPeriodStart(payPeriodDate);
            LocalDate periodEnd = contributionPeriodEnd(periodStart);
            boolean eligibleAtStart = currentGrossWage.compareTo(config.getWageThreshold()) <= 0;

            enrollment = new EmployeeEsiEnrollment();
            enrollment.setTenantId(tenantId);
            enrollment.setCompanyId(companyId);
            enrollment.setEmployee(employee);
            enrollment.setContributionPeriodStart(periodStart);
            enrollment.setContributionPeriodEnd(periodEnd);
            enrollment.setEligibleAtPeriodStart(eligibleAtStart);
            enrollment.setWageAtPeriodStart(scale(currentGrossWage));
            isNew = true;
        }

        if (!enrollment.isEligibleAtPeriodStart()) {
            return EsiResult.notEligible(enrollment, isNew);
        }

        BigDecimal employeeContribution = pct(currentGrossWage, config.getEmployeeRatePct());
        BigDecimal employerContribution = pct(currentGrossWage, config.getEmployerRatePct());
        return new EsiResult(
                scale(employeeContribution), scale(employerContribution), true, enrollment, isNew);
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal ratePct) {
        return base.multiply(ratePct).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
