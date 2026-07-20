package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PayrollCalculatorTest {

    private final PayrollCalculator calc = new PayrollCalculator();

    @Test
    void basicOnlyProducesSingleEarningLine() {
        EmployeeCompensation c = comp(new BigDecimal("5000.00"));

        PayrollCalculator.ComputedPayslip result = calc.compute(c);

        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).getComponentCodeSnapshot()).isEqualTo("BASIC");
        assertThat(result.lines().get(0).getKind()).isEqualTo(PayComponentKind.EARNING);
        assertThat(result.gross()).isEqualByComparingTo("5000.00");
        assertThat(result.deductions()).isEqualByComparingTo("0.00");
        assertThat(result.net()).isEqualByComparingTo("5000.00");
    }

    @Test
    void percentageDeductionComputesAgainstBasic() {
        EmployeeCompensation c = comp(new BigDecimal("4000.00"));
        PayComponent tax =
                component(
                        "INCOME_TAX",
                        PayComponentKind.DEDUCTION,
                        PayComponentCalculationType.PERCENT_OF_BASIC);
        c.addLine(line(tax, BigDecimal.ZERO, new BigDecimal("15.0000")));

        PayrollCalculator.ComputedPayslip result = calc.compute(c);

        assertThat(result.gross()).isEqualByComparingTo("4000.00");
        assertThat(result.deductions()).isEqualByComparingTo("600.00");
        assertThat(result.net()).isEqualByComparingTo("3400.00");
    }

    @Test
    void fixedEarningAddsToGross() {
        EmployeeCompensation c = comp(new BigDecimal("3000.00"));
        PayComponent hra =
                component("HRA", PayComponentKind.EARNING, PayComponentCalculationType.FIXED);
        c.addLine(line(hra, new BigDecimal("800.00"), BigDecimal.ZERO));

        PayrollCalculator.ComputedPayslip result = calc.compute(c);

        assertThat(result.gross()).isEqualByComparingTo("3800.00");
        assertThat(result.net()).isEqualByComparingTo("3800.00");
    }

    @Test
    void mixedEarningsAndDeductions() {
        EmployeeCompensation c = comp(new BigDecimal("6000.00"));
        c.addLine(
                line(
                        component(
                                "HRA", PayComponentKind.EARNING, PayComponentCalculationType.FIXED),
                        new BigDecimal("1000.00"),
                        BigDecimal.ZERO));
        c.addLine(
                line(
                        component(
                                "PF",
                                PayComponentKind.DEDUCTION,
                                PayComponentCalculationType.PERCENT_OF_BASIC),
                        BigDecimal.ZERO,
                        new BigDecimal("12.0000")));
        c.addLine(
                line(
                        component(
                                "TAX",
                                PayComponentKind.DEDUCTION,
                                PayComponentCalculationType.FIXED),
                        new BigDecimal("500.00"),
                        BigDecimal.ZERO));

        PayrollCalculator.ComputedPayslip result = calc.compute(c);

        // gross = basic (6000) + HRA (1000) = 7000
        // deductions = PF (12% of 6000 = 720) + TAX (500) = 1220
        // net = 7000 - 1220 = 5780
        assertThat(result.gross()).isEqualByComparingTo("7000.00");
        assertThat(result.deductions()).isEqualByComparingTo("1220.00");
        assertThat(result.net()).isEqualByComparingTo("5780.00");
    }

    @Test
    void inactiveComponentIsSkipped() {
        EmployeeCompensation c = comp(new BigDecimal("5000.00"));
        PayComponent inactive =
                component("OLD_BONUS", PayComponentKind.EARNING, PayComponentCalculationType.FIXED);
        inactive.setActive(false);
        c.addLine(line(inactive, new BigDecimal("999.00"), BigDecimal.ZERO));

        PayrollCalculator.ComputedPayslip result = calc.compute(c);

        assertThat(result.lines()).hasSize(1);
        assertThat(result.gross()).isEqualByComparingTo("5000.00");
    }

    @Test
    void netFloorsAtZeroWhenDeductionsExceedGross() {
        EmployeeCompensation c = comp(new BigDecimal("1000.00"));
        PayComponent bigTax =
                component("BIG_TAX", PayComponentKind.DEDUCTION, PayComponentCalculationType.FIXED);
        c.addLine(line(bigTax, new BigDecimal("2000.00"), BigDecimal.ZERO));

        PayrollCalculator.ComputedPayslip result = calc.compute(c);

        assertThat(result.net()).isEqualByComparingTo("0.00");
    }

    private static EmployeeCompensation comp(BigDecimal basic) {
        EmployeeCompensation c = new EmployeeCompensation();
        c.setBasicSalary(basic);
        c.setFrequency(PayrollFrequency.MONTHLY);
        return c;
    }

    private static PayComponent component(
            String code, PayComponentKind kind, PayComponentCalculationType calc) {
        PayComponent c = new PayComponent();
        c.setCode(code);
        c.setName(code);
        c.setKind(kind);
        c.setCalculationType(calc);
        c.setActive(true);
        return c;
    }

    private static EmployeeCompensationLine line(
            PayComponent component, BigDecimal amount, BigDecimal pct) {
        EmployeeCompensationLine l = new EmployeeCompensationLine();
        l.setPayComponent(component);
        l.setAmount(amount);
        l.setPercentage(pct);
        return l;
    }
}
