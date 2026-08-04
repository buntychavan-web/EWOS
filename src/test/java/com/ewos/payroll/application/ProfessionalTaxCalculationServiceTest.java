package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.payroll.domain.ProfessionalTaxSlab;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProfessionalTaxCalculationServiceTest {

    private final ProfessionalTaxCalculationService service =
            new ProfessionalTaxCalculationService();

    private static ProfessionalTaxSlab slab(
            String gender, String min, String max, String amount, String cap) {
        ProfessionalTaxSlab s = new ProfessionalTaxSlab();
        s.setGender(gender);
        s.setMinMonthlyIncome(new BigDecimal(min));
        s.setMaxMonthlyIncome(max == null ? null : new BigDecimal(max));
        s.setMonthlyTaxAmount(new BigDecimal(amount));
        s.setAnnualCapAmount(cap == null ? null : new BigDecimal(cap));
        s.setEffectiveFrom(LocalDate.of(2025, 4, 1));
        return s;
    }

    @Test
    void genderAgnosticSlabAppliesWhenNoGenderGiven() {
        List<ProfessionalTaxSlab> slabs =
                List.of(
                        slab(null, "0", "14999", "0", null),
                        slab(null, "15000", "24999", "200", null),
                        slab(null, "25000", null, "300", null));

        BigDecimal amount =
                service.calculate(slabs, null, new BigDecimal("20000"), BigDecimal.ZERO);
        assertThat(amount).isEqualByComparingTo("200.00");
    }

    @Test
    void genderSpecificSlabIsPreferredOverAgnosticSlabForSameBand() {
        List<ProfessionalTaxSlab> slabs =
                List.of(
                        slab(null, "0", "24999", "200", null),
                        slab("FEMALE", "0", "24999", "0", null));

        BigDecimal amount =
                service.calculate(slabs, "FEMALE", new BigDecimal("20000"), BigDecimal.ZERO);
        assertThat(amount).isEqualByComparingTo("0.00");

        BigDecimal maleAmount =
                service.calculate(slabs, "MALE", new BigDecimal("20000"), BigDecimal.ZERO);
        assertThat(maleAmount).isEqualByComparingTo("200.00");
    }

    @Test
    void annualCapReducesChargeOnceYtdIsNearTheLimit() {
        List<ProfessionalTaxSlab> slabs = List.of(slab(null, "10000", "14999", "150", "2400"));

        BigDecimal amount =
                service.calculate(slabs, null, new BigDecimal("12000"), new BigDecimal("2350"));
        assertThat(amount).isEqualByComparingTo("50.00");
    }

    @Test
    void annualCapAlreadyExhaustedChargesNothingMore() {
        List<ProfessionalTaxSlab> slabs = List.of(slab(null, "10000", "14999", "150", "2400"));

        BigDecimal amount =
                service.calculate(slabs, null, new BigDecimal("12000"), new BigDecimal("2400"));
        assertThat(amount).isEqualByComparingTo("0.00");
    }

    @Test
    void noMatchingBandYieldsZero() {
        List<ProfessionalTaxSlab> slabs = List.of(slab(null, "15000", "24999", "200", null));

        BigDecimal amount = service.calculate(slabs, null, new BigDecimal("5000"), BigDecimal.ZERO);
        assertThat(amount).isEqualByComparingTo("0");
    }
}
