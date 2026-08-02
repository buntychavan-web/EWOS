package com.ewos.payroll.application;

import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.IncomeTaxPolicy;
import com.ewos.payroll.domain.IncomeTaxSlab;
import com.ewos.payroll.domain.IncomeTaxSurchargeSlab;
import com.ewos.payroll.domain.TaxRegime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Monthly TDS projection. Every run re-projects the employee's annual salary from the current month
 * (an explicit, documented simplification — no run distributes months-elapsed differently),
 * computes the full annual tax liability, and recovers {@code (annualLiability - ytdTdsDeducted) /
 * monthsRemainingInFY} this month. Because every run re-projects and nets off what's already been
 * deducted, a mid-year salary change or a new declaration self-corrects on the very next run
 * without a separate year-end reconciliation pass. HRA/LTA exemptions and the Chapter VI-A
 * deduction only apply under the old regime — the new regime seed data zeroes them out at the
 * policy level, but this service also gates on {@code regime} explicitly so a misconfigured policy
 * row can never grant an old-regime exemption under the new regime. No marginal relief is applied
 * to the surcharge — see the Sprint 24H-1 design document. Pure: no persistence; the caller
 * persists the returned YTD deltas onto {@link EmployeeTaxDeclaration}.
 */
@Service
public class IncomeTaxCalculationService {

    private static final int MONEY_SCALE = 2;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal HRA_BASIC_RENT_OFFSET_PCT = BigDecimal.TEN;
    private static final BigDecimal HRA_METRO_PCT = new BigDecimal("50");
    private static final BigDecimal HRA_NON_METRO_PCT = new BigDecimal("40");

    public record TdsInput(
            BigDecimal monthlyTaxableSalary,
            BigDecimal monthlyBasic,
            BigDecimal monthlyHraReceived,
            int monthsRemainingInFiscalYear,
            EmployeeTaxDeclaration declaration) {}

    public record TdsResult(
            BigDecimal monthlyTdsRecovery,
            BigDecimal annualTaxLiability,
            BigDecimal projectedAnnualSalary,
            BigDecimal taxableIncome,
            BigDecimal hraExemption,
            BigDecimal ltaExemption) {

        static TdsResult zero() {
            BigDecimal z = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            return new TdsResult(z, z, z, z, z, z);
        }
    }

    public TdsResult calculate(
            TaxRegime regime,
            List<IncomeTaxSlab> slabs,
            IncomeTaxPolicy policy,
            List<IncomeTaxSurchargeSlab> surchargeSlabs,
            TdsInput input) {
        if (regime == null || policy == null || slabs == null || slabs.isEmpty() || input == null) {
            return TdsResult.zero();
        }
        BigDecimal monthlyTaxableSalary = nz(input.monthlyTaxableSalary());
        if (monthlyTaxableSalary.signum() <= 0) {
            return TdsResult.zero();
        }
        EmployeeTaxDeclaration declaration = input.declaration();
        boolean oldRegime = regime == TaxRegime.OLD;

        BigDecimal projectedAnnualSalary = scale(monthlyTaxableSalary.multiply(MONTHS_PER_YEAR));

        BigDecimal hraExemption = BigDecimal.ZERO;
        BigDecimal ltaExemption = BigDecimal.ZERO;
        if (oldRegime && declaration != null) {
            hraExemption =
                    computeHraExemption(
                            input.monthlyBasic(), input.monthlyHraReceived(), declaration);
            ltaExemption = nz(declaration.getLtaExemptionDeclared());
        }

        BigDecimal previousEmployerIncome =
                declaration == null ? BigDecimal.ZERO : nz(declaration.getPreviousEmployerIncome());
        BigDecimal otherIncome =
                declaration == null ? BigDecimal.ZERO : nz(declaration.getOtherIncome());
        BigDecimal housePropertyLoss =
                declaration == null ? BigDecimal.ZERO : nz(declaration.getHousePropertyLoss());
        BigDecimal cappedHousePropertyLoss =
                housePropertyLoss.min(policy.getHousePropertyLossCap());

        BigDecimal grossTotalIncome =
                projectedAnnualSalary
                        .subtract(hraExemption)
                        .subtract(ltaExemption)
                        .add(previousEmployerIncome)
                        .add(otherIncome)
                        .subtract(cappedHousePropertyLoss);

        BigDecimal chapterViaDeduction = BigDecimal.ZERO;
        if (oldRegime && declaration != null) {
            chapterViaDeduction =
                    nz(declaration.getChapterViaDeclaredAmount())
                            .min(policy.getChapterViaMaxDeduction());
        }

        BigDecimal taxableIncome =
                grossTotalIncome
                        .subtract(policy.getStandardDeduction())
                        .subtract(chapterViaDeduction)
                        .max(BigDecimal.ZERO);
        taxableIncome = scale(taxableIncome);

        BigDecimal slabTax = computeSlabTax(slabs, taxableIncome);

        BigDecimal taxAfterRebate;
        if (taxableIncome.compareTo(policy.getRebateIncomeThreshold()) <= 0) {
            BigDecimal rebate = slabTax.min(policy.getRebateMaxAmount());
            taxAfterRebate = slabTax.subtract(rebate).max(BigDecimal.ZERO);
        } else {
            // Section 87A marginal relief: just above the rebate threshold, tax payable is capped
            // to the amount income exceeds the threshold by, so crossing it by a rupee can never
            // cost more than that rupee (and never less than the ordinary slab tax would).
            BigDecimal excessOverThreshold =
                    taxableIncome.subtract(policy.getRebateIncomeThreshold());
            taxAfterRebate =
                    slabTax.compareTo(excessOverThreshold) > 0 ? excessOverThreshold : slabTax;
        }

        BigDecimal surchargeRate = findSurchargeRate(surchargeSlabs, taxableIncome);
        BigDecimal surcharge = pct(taxAfterRebate, surchargeRate);

        BigDecimal cess = pct(taxAfterRebate.add(surcharge), policy.getCessRatePct());

        BigDecimal annualTaxLiability = scale(taxAfterRebate.add(surcharge).add(cess));

        int monthsRemaining = Math.max(1, input.monthsRemainingInFiscalYear());
        BigDecimal ytdTdsDeducted =
                declaration == null ? BigDecimal.ZERO : nz(declaration.getYtdTdsDeducted());
        BigDecimal monthlyRecovery =
                annualTaxLiability
                        .subtract(ytdTdsDeducted)
                        .divide(
                                BigDecimal.valueOf(monthsRemaining),
                                MONEY_SCALE,
                                RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO);

        return new TdsResult(
                monthlyRecovery,
                annualTaxLiability,
                projectedAnnualSalary,
                taxableIncome,
                scale(hraExemption),
                scale(ltaExemption));
    }

    private BigDecimal computeHraExemption(
            BigDecimal monthlyBasic,
            BigDecimal monthlyHraReceived,
            EmployeeTaxDeclaration declaration) {
        BigDecimal basic = nz(monthlyBasic);
        BigDecimal hraReceived = nz(monthlyHraReceived);
        if (basic.signum() <= 0 || hraReceived.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal annualizedBasic = basic.multiply(MONTHS_PER_YEAR);
        BigDecimal annualizedHra = hraReceived.multiply(MONTHS_PER_YEAR);
        BigDecimal annualRentPaid = nz(declaration.getRentPaidAnnual());

        BigDecimal rentOverTenPctBasic =
                annualRentPaid
                        .subtract(pct(annualizedBasic, HRA_BASIC_RENT_OFFSET_PCT))
                        .max(BigDecimal.ZERO);
        BigDecimal metroLimit =
                pct(annualizedBasic, declaration.isMetroCity() ? HRA_METRO_PCT : HRA_NON_METRO_PCT);

        return annualizedHra.min(rentOverTenPctBasic).min(metroLimit).max(BigDecimal.ZERO);
    }

    private BigDecimal computeSlabTax(List<IncomeTaxSlab> slabs, BigDecimal taxableIncome) {
        BigDecimal tax = BigDecimal.ZERO;
        List<IncomeTaxSlab> ordered =
                slabs.stream().sorted(Comparator.comparing(IncomeTaxSlab::getMinIncome)).toList();
        for (IncomeTaxSlab slab : ordered) {
            if (taxableIncome.compareTo(slab.getMinIncome()) <= 0) {
                continue;
            }
            BigDecimal bracketTop =
                    slab.getMaxIncome() == null
                            ? taxableIncome
                            : slab.getMaxIncome().min(taxableIncome);
            BigDecimal bracketAmount =
                    bracketTop.subtract(slab.getMinIncome()).max(BigDecimal.ZERO);
            tax = tax.add(pct(bracketAmount, slab.getRatePct()));
        }
        return tax;
    }

    private BigDecimal findSurchargeRate(
            List<IncomeTaxSurchargeSlab> surchargeSlabs, BigDecimal taxableIncome) {
        if (surchargeSlabs == null) {
            return BigDecimal.ZERO;
        }
        return surchargeSlabs.stream()
                .filter(
                        slab ->
                                taxableIncome.compareTo(slab.getMinIncome()) >= 0
                                        && (slab.getMaxIncome() == null
                                                || taxableIncome.compareTo(slab.getMaxIncome())
                                                        <= 0))
                .map(IncomeTaxSurchargeSlab::getSurchargeRatePct)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal pct(BigDecimal base, BigDecimal ratePct) {
        if (ratePct == null || ratePct.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return base.multiply(ratePct).divide(ONE_HUNDRED, 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
