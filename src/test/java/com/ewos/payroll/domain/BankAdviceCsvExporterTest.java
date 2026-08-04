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
                row(
                        "EMP-101",
                        "Ada Lovelace",
                        "1234567890",
                        "******7890",
                        new BigDecimal("4200.00"),
                        "USD");

        String csv = exporter.export(advice, List.of(row));

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("advice_number,employee_number,");
        assertThat(lines[1]).startsWith("ADV-2026-07-01,EMP-101,Ada Lovelace,1234567890");
        assertThat(lines[1]).contains(",4200.00,");
        assertThat(lines[1]).endsWith(",PENDING");
    }

    @Test
    void writesTheRealAccountNumberNotTheMaskedOne() {
        BankAdvice advice = new BankAdvice();
        advice.setAdviceNumber("ADV-BUGFIX");
        PaymentInstruction row =
                row(
                        "EMP-1",
                        "Grace Hopper",
                        "9988776655",
                        "******6655",
                        new BigDecimal("500.00"),
                        "USD");

        String csv = exporter.export(advice, List.of(row));

        assertThat(csv).contains("9988776655");
        assertThat(csv).doesNotContain("******6655");
    }

    @Test
    void blankAccountNumberSnapshotRendersAsAnEmptyField() {
        BankAdvice advice = new BankAdvice();
        advice.setAdviceNumber("ADV-SKIPPED");
        PaymentInstruction row =
                row("EMP-2", "Skipped Employee", null, "", new BigDecimal("0.00"), "USD");

        String csv = exporter.export(advice, List.of(row));

        assertThat(csv).contains("Skipped Employee,,");
    }

    @Test
    void quotesFieldsContainingCommas() {
        BankAdvice advice = new BankAdvice();
        advice.setAdviceNumber("ADV,QUOTE");
        PaymentInstruction row =
                row("E-1", "Doe, Jane", "1112223333", "***9999", new BigDecimal("100.00"), "USD");
        String csv = exporter.export(advice, List.of(row));
        assertThat(csv).contains("\"ADV,QUOTE\"").contains("\"Doe, Jane\"");
    }

    @Test
    void escapesEmbeddedQuotesByDoubling() {
        BankAdvice advice = new BankAdvice();
        advice.setAdviceNumber("ADV1");
        PaymentInstruction row =
                row("E-1", "Say \"hi\"", "4445556666", "***1", new BigDecimal("1.00"), "USD");
        String csv = exporter.export(advice, List.of(row));
        assertThat(csv).contains("\"Say \"\"hi\"\"\"");
    }

    private static PaymentInstruction row(
            String employeeNumber,
            String holder,
            String realAccountNumber,
            String masked,
            BigDecimal amount,
            String currency) {
        PaymentInstruction p = new PaymentInstruction();
        Employee e = new Employee();
        e.setEmployeeNumber(employeeNumber);
        p.setEmployee(e);
        p.setAccountHolderSnapshot(holder);
        p.setAccountNumberSnapshot(realAccountNumber);
        p.setAccountNumberMasked(masked);
        p.setBankNameSnapshot("Acme Bank");
        p.setAmount(amount);
        p.setCurrency(currency);
        p.setStatus(PaymentInstructionStatus.PENDING);
        return p;
    }
}
