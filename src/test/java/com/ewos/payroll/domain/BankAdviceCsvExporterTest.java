package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.employee.domain.Employee;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BankAdviceCsvExporterTest {

    private final BankAdviceCsvExporter exporter = new BankAdviceCsvExporter();

    @Test
    void emitsHeaderAndOneRowPerInstruction() {
        BankAdvice advice = new BankAdvice();
        advice.setAdviceNumber("ADV-2026-07-01");
        advice.setAdviceDate(LocalDate.of(2026, 7, 5));

        PaymentInstruction row =
                row("EMP-101", "Ada Lovelace", "******1234", new BigDecimal("4200.00"), "USD");

        String csv = exporter.export(advice, List.of(row));

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("advice_number,employee_number,");
        assertThat(lines[1]).startsWith("ADV-2026-07-01,EMP-101,Ada Lovelace,******1234");
        assertThat(lines[1]).contains(",4200.00,");
        assertThat(lines[1]).endsWith(",PENDING");
    }

    @Test
    void quotesFieldsContainingCommas() {
        BankAdvice advice = new BankAdvice();
        advice.setAdviceNumber("ADV,QUOTE");
        PaymentInstruction row =
                row("E-1", "Doe, Jane", "***9999", new BigDecimal("100.00"), "USD");
        String csv = exporter.export(advice, List.of(row));
        assertThat(csv).contains("\"ADV,QUOTE\"").contains("\"Doe, Jane\"");
    }

    @Test
    void escapesEmbeddedQuotesByDoubling() {
        BankAdvice advice = new BankAdvice();
        advice.setAdviceNumber("ADV1");
        PaymentInstruction row = row("E-1", "Say \"hi\"", "***1", new BigDecimal("1.00"), "USD");
        String csv = exporter.export(advice, List.of(row));
        assertThat(csv).contains("\"Say \"\"hi\"\"\"");
    }

    private static PaymentInstruction row(
            String employeeNumber,
            String holder,
            String masked,
            BigDecimal amount,
            String currency) {
        PaymentInstruction p = new PaymentInstruction();
        Employee e = new Employee();
        e.setEmployeeNumber(employeeNumber);
        p.setEmployee(e);
        p.setAccountHolderSnapshot(holder);
        p.setAccountNumberMasked(masked);
        p.setBankNameSnapshot("Acme Bank");
        p.setAmount(amount);
        p.setCurrency(currency);
        p.setStatus(PaymentInstructionStatus.PENDING);
        return p;
    }
}
