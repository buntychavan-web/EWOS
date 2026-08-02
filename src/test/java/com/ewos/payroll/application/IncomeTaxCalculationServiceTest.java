package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.application.IncomeTaxCalculationService.TdsInput;
import com.ewos.payroll.application.IncomeTaxCalculationService.TdsResult;
import com.ewos.payroll.domain.EmployeeTaxDeclaration;
import com.ewos.payroll.domain.IncomeTaxPolicy;
import com.ewos.payroll.domain.IncomeTaxSlab;
import com.ewos.payroll.domain.IncomeTaxSurchargeSlab;
import com.ewos.payroll.domain.TaxRegime;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Known-answer tests using the FY2025-26 India slabs/policy seeded by migration V52. */
class IncomeTaxCalculationServiceTest {

    private final IncomeTaxCalculationService service = new IncomeTaxCalculationService();

    private static IncomeTaxSlab slab(String min, String max, String ratePct) {
        IncomeTaxSlab s = new IncomeTaxSlab();
        s.setMinIncome(new BigDecimal(min));
        s.setMaxIncome(max == null ? null : new BigDecimal(max));
        s.setRatePct(new BigDecimal(ratePct));
        return s;
    }

    private static List<IncomeTaxSlab> newRegimeSlabs() {
        return List.of(
                slab("0", "400000", "0"),
                slab("400000", "800000", "5"),
                slab("800000", "1200000", "10"),
                slab("1200000", "1600000", "15"),
                slab("1600000", "2000000", "20"),
                slab("2000000", "2400000", "25"),
                slab("2400000", null, "30"));
    }

    private static List<IncomeTaxSlab> oldRegimeSlabs() {
        return List.of(
                slab("0", "250000", "0"),
                slab("250000", "500000", "5"),
                slab("500000", "1000000", "20"),
                slab("1000000", null, "30"));
    }

    private static IncomeTaxPolicy newRegimePolicy() {
        IncomeTaxPolicy p = new IncomeTaxPolicy();
        p.setRebateIncomeThreshold(new BigDecimal("1200000"));
        p.setRebateMaxAmount(new BigDecimal("60000"));
        p.setCessRatePct(new BigDecimal("4"));
        p.setStandardDeduction(new BigDecimal("75000"));
        p.setChapterViaMaxDeduction(BigDecimal.ZERO);
        p.setHousePropertyLossCap(new BigDecimal("200000"));
        return p;
    }

    private static IncomeTaxPolicy oldRegimePolicy() {
        IncomeTaxPolicy p = new IncomeTaxPolicy();
        p.setRebateIncomeThreshold(new BigDecimal("500000"));
        p.setRebateMaxAmount(new BigDecimal("12500"));
        p.setCessRatePct(new BigDecimal("4"));
        p.setStandardDeduction(new BigDecimal("50000"));
        p.setChapterViaMaxDeduction(new BigDecimal("150000"));
        p.setHousePropertyLossCap(new BigDecimal("200000"));
        return p;
    }

    private static IncomeTaxSurchargeSlab surchargeSlab(String min, String max, String ratePct) {
        IncomeTaxSurchargeSlab s = new IncomeTaxSurchargeSlab();
        s.setMinIncome(new BigDecimal(min));
        s.setMaxIncome(max == null ? null : new BigDecimal(max));
        s.setSurchargeRatePct(new BigDecimal(ratePct));
        return s;
    }

    private static List<IncomeTaxSurchargeSlab> newRegimeSurchargeSlabs() {
        return List.of(
                surchargeSlab("5000000", "10000000", "10"),
                surchargeSlab("10000000", "20000000", "15"),
                surchargeSlab("20000000", null, "25"));
    }

    @Test
    void newRegimeBelowRebateThresholdOwesNothing() {
        TdsInput input =
                new TdsInput(new BigDecimal("100000"), BigDecimal.ZERO, BigDecimal.ZERO, 12, null);
        TdsResult r =
                service.calculate(
                        TaxRegime.NEW, newRegimeSlabs(), newRegimePolicy(), List.of(), input);

        assertThat(r.taxableIncome()).isEqualByComparingTo("1125000.00");
        assertThat(r.annualTaxLiability()).isEqualByComparingTo("0.00");
        assertThat(r.monthlyTdsRecovery()).isEqualByComparingTo("0.00");
    }

    @Test
    void newRegimeAboveRebateThresholdComputesSlabTaxPlusCess() {
        TdsInput input =
                new TdsInput(new BigDecimal("200000"), BigDecimal.ZERO, BigDecimal.ZERO, 12, null);
        TdsResult r =
                service.calculate(
                        TaxRegime.NEW, newRegimeSlabs(), newRegimePolicy(), List.of(), input);

        assertThat(r.taxableIncome()).isEqualByComparingTo("2325000.00");
        assertThat(r.annualTaxLiability()).isEqualByComparingTo("292500.00");
        assertThat(r.monthlyTdsRecovery()).isEqualByComparingTo("24375.00");
    }

    @Test
    void newRegimeHighIncomeAppliesSurchargeWithNoMarginalRelief() {
        TdsInput input =
                new TdsInput(new BigDecimal("600000"), BigDecimal.ZERO, BigDecimal.ZERO, 12, null);
        TdsResult r =
                service.calculate(
                        TaxRegime.NEW,
                        newRegimeSlabs(),
                        newRegimePolicy(),
                        newRegimeSurchargeSlabs(),
                        input);

        assertThat(r.taxableIncome()).isEqualByComparingTo("7125000.00");
        assertThat(r.annualTaxLiability()).isEqualByComparingTo("1964820.00");
        assertThat(r.monthlyTdsRecovery()).isEqualByComparingTo("163735.00");
    }

    @Test
    void oldRegimeAppliesHraLtaAndChapterViaExemptionsThenNetsOffYtdTds() {
        EmployeeTaxDeclaration declaration = new EmployeeTaxDeclaration();
        declaration.setRegime(TaxRegime.OLD);
        declaration.setRentPaidAnnual(new BigDecimal("240000"));
        declaration.setMetroCity(true);
        declaration.setChapterViaDeclaredAmount(new BigDecimal("150000"));
        declaration.setLtaExemptionDeclared(new BigDecimal("20000"));
        declaration.setYtdTdsDeducted(new BigDecimal("10000"));

        TdsInput input =
                new TdsInput(
                        new BigDecimal("80000"),
                        new BigDecimal("40000"),
                        new BigDecimal("16000"),
                        6,
                        declaration);
        TdsResult r =
                service.calculate(
                        TaxRegime.OLD, oldRegimeSlabs(), oldRegimePolicy(), List.of(), input);

        assertThat(r.hraExemption()).isEqualByComparingTo("192000.00");
        assertThat(r.taxableIncome()).isEqualByComparingTo("548000.00");
        assertThat(r.annualTaxLiability()).isEqualByComparingTo("22984.00");
        assertThat(r.monthlyTdsRecovery()).isEqualByComparingTo("2164.00");
    }

    @Test
    void newRegimeGatesOffHraAndChapterViaEvenIfDeclarationSuppliesThem() {
        EmployeeTaxDeclaration declaration = new EmployeeTaxDeclaration();
        declaration.setRegime(TaxRegime.NEW);
        declaration.setRentPaidAnnual(new BigDecimal("240000"));
        declaration.setMetroCity(true);
        declaration.setChapterViaDeclaredAmount(new BigDecimal("150000"));

        TdsInput input =
                new TdsInput(
                        new BigDecimal("100000"),
                        new BigDecimal("40000"),
                        new BigDecimal("16000"),
                        12,
                        declaration);
        TdsResult r =
                service.calculate(
                        TaxRegime.NEW, newRegimeSlabs(), newRegimePolicy(), List.of(), input);

        assertThat(r.hraExemption()).isEqualByComparingTo("0.00");
        assertThat(r.taxableIncome()).isEqualByComparingTo("1125000.00");
    }

    @Test
    void zeroSalaryYieldsZeroResult() {
        TdsResult r =
                service.calculate(
                        TaxRegime.NEW,
                        newRegimeSlabs(),
                        newRegimePolicy(),
                        List.of(),
                        new TdsInput(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 12, null));
        assertThat(r.monthlyTdsRecovery()).isEqualByComparingTo("0");
    }
}
