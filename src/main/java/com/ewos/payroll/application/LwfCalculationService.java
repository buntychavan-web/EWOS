package com.ewos.payroll.application;

import com.ewos.payroll.domain.LwfConfiguration;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * Labour Welfare Fund: flat employee/employer amounts, charged only in the scheme's configured
 * remittance months (every month if unconfigured). Pure: takes the already-resolved LWF
 * configuration for the employee's jurisdiction.
 */
@Service
public class LwfCalculationService {

    public record LwfResult(BigDecimal employeeContribution, BigDecimal employerContribution) {

        static final LwfResult ZERO = new LwfResult(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public LwfResult calculate(LwfConfiguration config, LocalDate payPeriodDate) {
        if (config == null
                || payPeriodDate == null
                || !config.appliesInMonth(payPeriodDate.getMonthValue())) {
            return LwfResult.ZERO;
        }
        return new LwfResult(config.getEmployeeContribution(), config.getEmployerContribution());
    }
}
