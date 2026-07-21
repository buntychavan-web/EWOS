package com.ewos.payroll.domain;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CSV writer for bank advices. Emits a header row followed by one row per pending payment
 * instruction: {@code adviceNumber, employeeNumber, accountHolder, accountNumberMasked,
 * routingCode, swiftBic, amount, currency, status}. Fields are RFC-4180 quoted only when they
 * contain a comma, quote, or newline; embedded quotes are escaped by doubling.
 */
@Component
public final class BankAdviceCsvExporter {

    private static final String HEADER =
            "advice_number,employee_number,account_holder,account_number,routing_code,swift_bic,"
                    + "amount,currency,status";

    public String export(BankAdvice advice, List<PaymentInstruction> instructions) {
        StringBuilder sb = new StringBuilder(256 + instructions.size() * 128);
        sb.append(HEADER).append('\n');
        for (PaymentInstruction p : instructions) {
            String employeeNumber =
                    p.getEmployee() != null && p.getEmployee().getEmployeeNumber() != null
                            ? p.getEmployee().getEmployeeNumber()
                            : "";
            String amountStr = p.getAmount() != null ? p.getAmount().toPlainString() : "0";
            sb.append(csv(advice.getAdviceNumber()))
                    .append(',')
                    .append(csv(employeeNumber))
                    .append(',')
                    .append(csv(p.getAccountHolderSnapshot()))
                    .append(',')
                    .append(csv(p.getAccountNumberMasked()))
                    .append(',')
                    .append(csv(nullSafe(p.getRoutingCodeSnapshot())))
                    .append(',')
                    .append(csv(nullSafe(p.getSwiftBicSnapshot())))
                    .append(',')
                    .append(amountStr)
                    .append(',')
                    .append(csv(p.getCurrency()))
                    .append(',')
                    .append(csv(p.getStatus().name()))
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
        if (!needsQuote) {
            return v;
        }
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
