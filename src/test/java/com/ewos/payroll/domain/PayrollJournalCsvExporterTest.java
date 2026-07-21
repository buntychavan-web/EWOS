package com.ewos.payroll.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PayrollJournalCsvExporterTest {

    private final PayrollJournalCsvExporter exporter = new PayrollJournalCsvExporter();

    @Test
    void emitsHeaderAndOneRowPerLine() {
        PayrollJournal j = new PayrollJournal();
        j.setJournalNumber("JV-2026-07-01");
        j.setJournalDate(LocalDate.of(2026, 7, 5));
        PayrollJournalLine line = new PayrollJournalLine();
        line.setLineNo(1);
        line.setGlAccountCode("5100");
        line.setGlAccountNameSnapshot("Salary Expense");
        line.setAccountTypeSnapshot(GLAccountType.EXPENSE);
        line.setCostCentreCode("CC-100");
        line.setSourceKind(PayrollJournalLineSourceKind.PAY_COMPONENT);
        line.setSourceReference("payslip=abc");
        line.setDebitAmount(new BigDecimal("5000.0000"));
        line.setCreditAmount(BigDecimal.ZERO);
        line.setCurrency("USD");
        line.setDescription("BASIC");
        j.addLine(line);

        String csv = exporter.export(j);

        String[] lines = csv.split("\n");
        assertThat(lines).hasSize(2);
        assertThat(lines[0]).startsWith("journal_number,journal_date,line_no,");
        assertThat(lines[1])
                .contains("JV-2026-07-01")
                .contains("5100")
                .contains("CC-100")
                .contains("EXPENSE");
    }
}
