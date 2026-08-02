package com.ewos.payroll.application;

import com.ewos.payroll.domain.PfConfiguration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Provident Fund math — see the Sprint 24H-1 design document for the formula and worked example.
 * Pure: takes an already-resolved {@link PfConfiguration}, does no persistence of its own.
 */
@Service
public class PfCalculationService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    /**
     * @param employeeContribution employee PF + VPF — the payslip deduction line
     * @param employerPfContribution employer's residual EPF share (employer total minus EPS)
     * @param epsContribution the EPS carve-out of the employer's contribution
     * @param pfWageUsed the (possibly ceiling-capped) wage the PF/EPS percentages were applied to
     */
    public record PfResult(
            BigDecimal employeeContribution,
            BigDecimal employerPfContribution,
            BigDecimal epsContribution,
            BigDecimal pfWageUsed) {

        static PfResult zero() {
            return new PfResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    public PfResult calculate(
            PfConfiguration config,
            BigDecimal grossPfWage,
            boolean internationalWorker,
            boolean vpfEnabled,
            BigDecimal vpfPercentage) {
        if (config == null || grossPfWage == null || grossPfWage.signum() <= 0) {
            return PfResult.zero();
        }

        BigDecimal pfWage =
                internationalWorker ? grossPfWage : grossPfWage.min(config.getWageCeiling());

        BigDecimal employeePf = pct(pfWage, config.getEmployeeRatePct());
        BigDecimal vpf =
                vpfEnabled && vpfPercentage != null && vpfPercentage.signum() > 0
                        ? pct(grossPfWage, vpfPercentage)
                        : BigDecimal.ZERO;
        BigDecimal employeeContribution = scale(employeePf.add(vpf));

        BigDecimal epsWage = pfWage.min(config.getEpsWageCeiling());
        BigDecimal eps = pct(epsWage, config.getEpsRatePct());
        BigDecimal employerTotal = pct(pfWage, config.getEmployerPfRatePct());
        BigDecimal employerPf = scale(employerTotal.subtract(eps)).max(BigDecimal.ZERO);

        return new PfResult(employeeContribution, employerPf, scale(eps), scale(pfWage));
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal ratePct) {
        return base.multiply(ratePct).divide(ONE_HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
