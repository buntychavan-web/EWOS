package com.ewos.payroll.domain;

import org.springframework.stereotype.Component;

/**
 * Generic CSV writer for payroll journals. Downstream ERP-specific writers (SAP IDoc, Oracle EBS
 * staging, Microsoft Dynamics D365 generic journal) plug in as separate {@code
 * PayrollJournalExporter} implementations behind {@link BankAdviceCsvExporter}-style interfaces —
 * this class is the default and the pattern.
 */
@Component
public final class PayrollJournalCsvExporter {

    private static final String HEADER =
            "journal_number,journal_date,line_no,gl_account_code,gl_account_name,account_type,"
                    + "cost_centre,business_unit,department,source_kind,source_reference,"
                    + "debit,credit,currency,description";

    public String export(PayrollJournal journal) {
        StringBuilder sb = new StringBuilder(256 + journal.getLines().size() * 128);
        sb.append(HEADER).append('\n');
        for (PayrollJournalLine line : journal.getLines()) {
            String amountDr =
                    line.getDebitAmount() != null ? line.getDebitAmount().toPlainString() : "0";
            String amountCr =
                    line.getCreditAmount() != null ? line.getCreditAmount().toPlainString() : "0";
            sb.append(csv(journal.getJournalNumber()))
                    .append(',')
                    .append(journal.getJournalDate())
                    .append(',')
                    .append(line.getLineNo())
                    .append(',')
                    .append(csv(line.getGlAccountCode()))
                    .append(',')
                    .append(csv(line.getGlAccountNameSnapshot()))
                    .append(',')
                    .append(line.getAccountTypeSnapshot().name())
                    .append(',')
                    .append(csv(nullSafe(line.getCostCentreCode())))
                    .append(',')
                    .append(csv(nullSafe(line.getBusinessUnitCode())))
                    .append(',')
                    .append(csv(nullSafe(line.getDepartmentCode())))
                    .append(',')
                    .append(line.getSourceKind().name())
                    .append(',')
                    .append(csv(nullSafe(line.getSourceReference())))
                    .append(',')
                    .append(amountDr)
                    .append(',')
                    .append(amountCr)
                    .append(',')
                    .append(csv(line.getCurrency()))
                    .append(',')
                    .append(csv(nullSafe(line.getDescription())))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String csv(String v) {
        if (v == null) {
            return "";
        }
        boolean needsQuote =
                v.indexOf(',') >= 0
                        || v.indexOf('"') >= 0
                        || v.indexOf('\n') >= 0
                        || v.indexOf('\r') >= 0;
        return needsQuote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
