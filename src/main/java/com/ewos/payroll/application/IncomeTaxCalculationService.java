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
 * Monthly TDS projection. Every run re-projects the employee's annual salary from the current
 * month's <em>recurring</em> pay (an explicit, documented simplification — no run distributes
 * months-elapsed differently), computes the full annual tax liability, and recovers {@code
 * (annualLiability - ytdTdsDeducted) / monthsRemainingInFY} this month. Because every run
 * re-projects and nets off what's already been deducted, a mid-year salary change or a new
 * declaration self-corrects on the very next run without a separate year-end reconciliation pass.
 * HRA/LTA exemptions and the Chapter VI-A deduction only apply under the old regime — the new
 * regime seed data zeroes them out at the policy level, but this service also gates on {@code
 * regime} explicitly so a misconfigured policy row can never grant an old-regime exemption under
 * the new regime. No marginal relief is applied to the surcharge — see the Sprint 24H-1 design
 * document. Pure: no persistence; the caller persists the returned YTD deltas onto {@link
 * EmployeeTaxDeclaration}.
 *
 * <p><b>Sprint 24K §8.2 — prorated recovery.</b> {@code monthlyRecurringTaxableSalary} is the
 * employee's normal recurring monthly figure (what the projection annualises), while {@code
 * payableEarningsThisPeriod} is what's actually payable this specific period. When the two diverge
 * — a new joiner's first partial month, LOP, a salary hold — the even-share recovery is prorated
 * down by the same ratio, rather than deducting the full normal amount against a shrunken payslip.
 * The un-recovered amount is never separately stored: because {@code ytdTdsDeducted} only ever
 * reflects what was <em>actually</em> recovered, the very next run's even-share division naturally
 * redistributes the shortfall across the remaining months — the same self-correcting mechanism
 * already documented above for salary changes. {@link TdsResult#shortfallCarriedForward()} exposes
 * the amount for audit logging by the caller.
 *
 * <p><b>Sprint 24K §8.3 — tax on variable payments.</b> {@code oneTimePaymentThisPeriod} (bonus,
 * incentive, ex-gratia, one-off arrears) is deliberately <em>not</em> multiplied by 12 the way
 * recurring salary is — doing so would grossly overstate projected annual income for a single bonus
 * month. Instead the engine computes the annual tax liability twice — once on the recurring salary
 * alone ({@link TdsResult#recurringAnnualTaxLiability()}, the baseline every future month's
 * even-share redistribution uses) and once with the one-time payment added on top — and recovers
 * only the <em>difference</em> ({@link TdsResult#incrementalTaxOnOneTimePayment()}) in full this
 * period. That incremental amount is deliberately kept out of {@code ytdTdsDeducted} (the caller
 * persists it onto {@link EmployeeTaxDeclaration#getYtdVariablePaymentTdsRecovered()} instead), so
 * a bonus recovered in full this month never suppresses next month's normal recurring recovery.
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
            BigDecimal monthlyRecurringTaxableSalary,
            BigDecimal oneTimePaymentThisPeriod,
            BigDecimal monthlyBasic,
            BigDecimal monthlyHraReceived,
            int monthsRemainingInFiscalYear,
            BigDecimal payableEarningsThisPeriod,
            EmployeeTaxDeclaration declaration) {}

    public record TdsResult(
            BigDecimal monthlyTdsRecovery,
            BigDecimal recurringTdsRecovery,
            BigDecimal incrementalTaxOnOneTimePayment,
            BigDecimal shortfallCarriedForward,
            BigDecimal annualTaxLiability,
            BigDecimal recurringAnnualTaxLiability,
            BigDecimal projectedAnnualSalary,
            BigDecimal taxableIncome,
            BigDecimal hraExemption,
            BigDecimal ltaExemption) {

        static TdsResult zero() {
            BigDecimal z = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            return new TdsResult(z, z, z, z, z, z, z, z, z, z);
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
        BigDecimal monthlyRecurringTaxableSalary = nz(input.monthlyRecurringTaxableSalary());
        if (monthlyRecurringTaxableSalary.signum() <= 0) {
            return TdsResult.zero();
        }
        EmployeeTaxDeclaration declaration = input.declaration();
        boolean oldRegime = regime == TaxRegime.OLD;

        BigDecimal projectedAnnualSalary =
                scale(monthlyRecurringTaxableSalary.multiply(MONTHS_PER_YEAR));

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

        BigDecimal recurringAnnualTaxLiability =
                computeAnnualTax(slabs, policy, surchargeSlabs, taxableIncome);

        // §8.3 — a one-time payment is taxed as the incremental liability it causes on top of the
        // recurring baseline, never by annualising the payment itself.
        BigDecimal oneTimePayment = nz(input.oneTimePaymentThisPeriod());
        BigDecimal annualTaxLiability = recurringAnnualTaxLiability;
        BigDecimal incrementalTax = BigDecimal.ZERO;
        if (oneTimePayment.signum() > 0) {
            BigDecimal taxableIncomeWithBonus = scale(taxableIncome.add(oneTimePayment));
            annualTaxLiability =
                    computeAnnualTax(slabs, policy, surchargeSlabs, taxableIncomeWithBonus);
            incrementalTax =
                    annualTaxLiability.subtract(recurringAnnualTaxLiability).max(BigDecimal.ZERO);
        }

        int monthsRemaining = Math.max(1, input.monthsRemainingInFiscalYear());
        BigDecimal ytdTdsDeducted =
                declaration == null ? BigDecimal.ZERO : nz(declaration.getYtdTdsDeducted());
        BigDecimal recurringEvenShare =
                recurringAnnualTaxLiability
                        .subtract(ytdTdsDeducted)
                        .divide(
                                BigDecimal.valueOf(monthsRemaining),
                                MONEY_SCALE,
                                RoundingMode.HALF_UP)
                        .max(BigDecimal.ZERO);

        // §8.2 — prorate the recurring even-share against what's actually payable this period; the
        // shortfall self-corrects on the next run because ytdTdsDeducted only ever reflects what
        // was actually recovered.
        BigDecimal recurringRecovery = recurringEvenShare;
        BigDecimal shortfall = BigDecimal.ZERO;
        BigDecimal payableThisPeriod = input.payableEarningsThisPeriod();
        if (payableThisPeriod != null
                && payableThisPeriod.compareTo(monthlyRecurringTaxableSalary) < 0) {
            BigDecimal ratio =
                    payableThisPeriod
                            .max(BigDecimal.ZERO)
                            .divide(monthlyRecurringTaxableSalary, 10, RoundingMode.HALF_UP);
            recurringRecovery = scale(recurringEvenShare.multiply(ratio));
            shortfall = recurringEvenShare.subtract(recurringRecovery).max(BigDecimal.ZERO);
        }

        BigDecimal totalRecovery = scale(recurringRecovery.add(incrementalTax));

        return new TdsResult(
                totalRecovery,
                recurringRecovery,
                scale(incrementalTax),
                shortfall,
                annualTaxLiability,
                recurringAnnualTaxLiability,
                projectedAnnualSalary,
                taxableIncome,
                scale(hraExemption),
                scale(ltaExemption));
    }

    private BigDecimal computeAnnualTax(
            List<IncomeTaxSlab> slabs,
            IncomeTaxPolicy policy,
            List<IncomeTaxSurchargeSlab> surchargeSlabs,
            BigDecimal taxableIncome) {
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

        return scale(taxAfterRebate.add(surcharge).add(cess));
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
