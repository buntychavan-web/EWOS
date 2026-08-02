package com.ewos.payroll.application;

import com.ewos.payroll.domain.GratuityConfiguration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import org.springframework.stereotype.Service;

/**
 * Statutory gratuity: {@code completedYears} rounds a part-year up to a full year only once it
 * exceeds six months (Payment of Gratuity Act s.4(2)). Eligibility normally requires {@code
 * completedYears >= minYearsEligibility}; the five-year rule can only be waived for the two
 * statutory grounds, death or disablement — never a discretionary override. Pure: no persistence.
 */
@Service
public class GratuityCalculationService {

    private static final int MONEY_SCALE = 2;

    public enum WaiverReason {
        DEATH,
        DISABLEMENT
    }

    public record GratuityResult(BigDecimal amount, boolean eligible, int completedYears) {

        static GratuityResult notEligible(int completedYears) {
            return new GratuityResult(BigDecimal.ZERO, false, completedYears);
        }
    }

    public GratuityResult calculate(
            GratuityConfiguration config,
            LocalDate dateOfJoining,
            LocalDate asOfDate,
            BigDecimal lastDrawnBasic,
            WaiverReason waiverReason) {
        if (config == null
                || dateOfJoining == null
                || asOfDate == null
                || !asOfDate.isAfter(dateOfJoining)) {
            return GratuityResult.notEligible(0);
        }

        Period service = Period.between(dateOfJoining, asOfDate);
        int completedYears = service.getYears() + (service.getMonths() > 6 ? 1 : 0);

        boolean waived =
                waiverReason == WaiverReason.DEATH || waiverReason == WaiverReason.DISABLEMENT;
        boolean meetsMinService =
                BigDecimal.valueOf(completedYears).compareTo(config.getMinYearsEligibility()) >= 0;
        if (!meetsMinService && !waived) {
            return GratuityResult.notEligible(completedYears);
        }
        if (lastDrawnBasic == null || lastDrawnBasic.signum() <= 0) {
            return GratuityResult.notEligible(completedYears);
        }

        BigDecimal rate =
                lastDrawnBasic
                        .multiply(BigDecimal.valueOf(config.getRateNumerator()))
                        .divide(
                                BigDecimal.valueOf(config.getRateDenominator()),
                                10,
                                RoundingMode.HALF_UP);
        BigDecimal rawAmount = rate.multiply(BigDecimal.valueOf(completedYears));
        BigDecimal cappedAmount = rawAmount.min(config.getStatutoryCeiling());
        return new GratuityResult(
                cappedAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP), true, completedYears);
    }
}
