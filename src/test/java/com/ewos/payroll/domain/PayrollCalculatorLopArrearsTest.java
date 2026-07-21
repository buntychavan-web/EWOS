package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PayrollCalculatorLopArrearsTest {

    private final PayrollCalculator calc = new PayrollCalculator();

    @Test
    void lopReducesBasicPercentAppliedToReducedBasic() {
        EmployeeCompensation c = new EmployeeCompensation();
        c.setBasicSalary(new BigDecimal("22000.00"));
        c.setFrequency(PayrollFrequency.MONTHLY);

        PayComponent tax = new PayComponent();
        tax.setCode("TAX");
        tax.setName("Tax");
        tax.setKind(PayComponentKind.DEDUCTION);
        tax.setCalculationType(PayComponentCalculationType.PERCENT_OF_BASIC);
        tax.setActive(true);
        EmployeeCompensationLine taxLine = new EmployeeCompensationLine();
        taxLine.setPayComponent(tax);
        taxLine.setPercentage(new BigDecimal("10.0000"));
        c.addLine(taxLine);

        // 2 LOP days out of 22 working days → basic reduces to 20000.
        PayrollCalculator.ComputedPayslip result =
                calc.compute(c, new BigDecimal("2"), new BigDecimal("22"), List.of());

        assertThat(result.gross()).isEqualByComparingTo("20000.00");
        // 10% of 20000 = 2000
        assertThat(result.deductions()).isEqualByComparingTo("2000.00");
        assertThat(result.net()).isEqualByComparingTo("18000.00");
        assertThat(result.lopDays()).isEqualByComparingTo("2");
        assertThat(result.basicApplied()).isEqualByComparingTo("20000.00");
    }

    @Test
    void earningArrearAddsToGross() {
        EmployeeCompensation c = new EmployeeCompensation();
        c.setBasicSalary(new BigDecimal("5000.00"));
        c.setFrequency(PayrollFrequency.MONTHLY);

        PayrollArrear a = new PayrollArrear();
        a.setReasonCode("RETRO_HIKE");
        a.setDescription("Retro salary hike Apr-Jun");
        a.setAmount(new BigDecimal("1500.00"));
        a.setKind(PayComponentKind.EARNING);

        PayrollCalculator.ComputedPayslip result =
                calc.compute(c, BigDecimal.ZERO, BigDecimal.ZERO, List.of(a));

        assertThat(result.gross()).isEqualByComparingTo("6500.00");
        assertThat(result.deductions()).isEqualByComparingTo("0.00");
        assertThat(result.net()).isEqualByComparingTo("6500.00");
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(1).getComponentCodeSnapshot()).isEqualTo("ARREAR_RETRO_HIKE");
    }

    @Test
    void deductionArrearReducesNet() {
        EmployeeCompensation c = new EmployeeCompensation();
        c.setBasicSalary(new BigDecimal("5000.00"));
        c.setFrequency(PayrollFrequency.MONTHLY);

        PayrollArrear a = new PayrollArrear();
        a.setReasonCode("OVERPAYMENT");
        a.setAmount(new BigDecimal("400.00"));
        a.setKind(PayComponentKind.DEDUCTION);

        PayrollCalculator.ComputedPayslip result =
                calc.compute(c, BigDecimal.ZERO, BigDecimal.ZERO, List.of(a));

        assertThat(result.gross()).isEqualByComparingTo("5000.00");
        assertThat(result.deductions()).isEqualByComparingTo("400.00");
        assertThat(result.net()).isEqualByComparingTo("4600.00");
    }

    @Test
    void lopAndArrearCombine() {
        EmployeeCompensation c = new EmployeeCompensation();
        c.setBasicSalary(new BigDecimal("22000.00"));
        c.setFrequency(PayrollFrequency.MONTHLY);

        PayrollArrear a = new PayrollArrear();
        a.setReasonCode("BONUS");
        a.setAmount(new BigDecimal("500.00"));
        a.setKind(PayComponentKind.EARNING);

        PayrollCalculator.ComputedPayslip result =
                calc.compute(c, new BigDecimal("2"), new BigDecimal("22"), List.of(a));

        // basic reduced to 20000, plus 500 arrear = 20500 gross
        assertThat(result.gross()).isEqualByComparingTo("20500.00");
        assertThat(result.net()).isEqualByComparingTo("20500.00");
    }
}
