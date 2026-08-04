package com.ewos.payroll.domain;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * CSV writer for bank advices. Emits a header row followed by one row per pending payment
 * instruction: {@code adviceNumber, employeeNumber, accountHolder, accountNumber, routingCode,
 * swiftBic, amount, currency, status}. Fields are RFC-4180 quoted only when they contain a comma,
 * quote, or newline; embedded quotes are escaped by doubling.
 *
 * <p>{@code account_number} carries the real account number ({@link
 * PaymentInstruction#getAccountNumberSnapshot()}), not the masked display value — a bank cannot
 * actually credit an employee from a masked number. This export endpoint requires {@code
 * PAYROLL_ADMIN} ({@code BankAdviceController#export}), a strictly higher bar than the {@code
 * PAYROLL_READ} every other bank-advice read requires, distinct from the masked-only {@code
 * PaymentInstructionResponse} every ordinary API response uses.
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
                    .append(csv(nullSafe(p.getAccountNumberSnapshot())))
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
