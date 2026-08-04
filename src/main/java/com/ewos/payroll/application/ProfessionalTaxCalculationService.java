package com.ewos.payroll.application;

import com.ewos.payroll.domain.ProfessionalTaxSlab;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * State-wise, income-banded Professional Tax. A gender-specific slab (e.g. a higher exemption
 * threshold some states grant women) is preferred over a gender-agnostic slab covering the same
 * income band. When a slab declares an {@code annualCapAmount}, the amount charged this month is
 * reduced so the employee's fiscal-year cumulative PT never exceeds the cap. Pure: takes the
 * already-resolved, effective-dated candidate slabs for the employee's jurisdiction.
 */
@Service
public class ProfessionalTaxCalculationService {

    private static final int MONEY_SCALE = 2;

    public BigDecimal calculate(
            List<ProfessionalTaxSlab> candidateSlabs,
            String employeeGender,
            BigDecimal monthlyIncome,
            BigDecimal ytdProfessionalTaxPaid) {
        if (candidateSlabs == null
                || candidateSlabs.isEmpty()
                || monthlyIncome == null
                || monthlyIncome.signum() <= 0) {
            return BigDecimal.ZERO;
        }

        List<ProfessionalTaxSlab> inBand =
                candidateSlabs.stream()
                        .filter(slab -> matchesIncomeBand(slab, monthlyIncome))
                        .toList();
        if (inBand.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Optional<ProfessionalTaxSlab> genderSpecific =
                employeeGender == null
                        ? Optional.empty()
                        : inBand.stream()
                                .filter(slab -> employeeGender.equalsIgnoreCase(slab.getGender()))
                                .max(
                                        Comparator.comparing(
                                                ProfessionalTaxSlab::getMinMonthlyIncome));

        ProfessionalTaxSlab selected =
                genderSpecific.orElseGet(
                        () ->
                                inBand.stream()
                                        .filter(slab -> slab.getGender() == null)
                                        .max(
                                                Comparator.comparing(
                                                        ProfessionalTaxSlab::getMinMonthlyIncome))
                                        .orElse(null));
        if (selected == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal rawAmount = selected.getMonthlyTaxAmount();
        if (selected.getAnnualCapAmount() == null) {
            return scale(rawAmount);
        }

        BigDecimal alreadyPaid =
                ytdProfessionalTaxPaid == null ? BigDecimal.ZERO : ytdProfessionalTaxPaid;
        BigDecimal remainingUnderCap =
                selected.getAnnualCapAmount().subtract(alreadyPaid).max(BigDecimal.ZERO);
        return scale(rawAmount.min(remainingUnderCap));
    }

    private static boolean matchesIncomeBand(ProfessionalTaxSlab slab, BigDecimal monthlyIncome) {
        boolean aboveMin = monthlyIncome.compareTo(slab.getMinMonthlyIncome()) >= 0;
        boolean belowMax =
                slab.getMaxMonthlyIncome() == null
                        || monthlyIncome.compareTo(slab.getMaxMonthlyIncome()) <= 0;
        return aboveMin && belowMax;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, java.math.RoundingMode.HALF_UP);
    }
}
