package com.ewos.payroll.domain;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Writes the EPFO Electronic Challan-cum-Return (ECR) v2.0 text file: one {@code #~#}-delimited
 * line per member, in the standard field order (UAN, member name, gross/EPF/EPS/EDLI wages, EPF
 * contribution remitted, EPS contribution remitted, employee's own EPF contribution remitted, NCP
 * days, refund of advances). This is the file a company uploads to the EPFO unified portal to file
 * its monthly PF return — {@link StatutoryChallan} only tracked the filing as a status transition
 * with a manually-entered reference before this; it never produced the file itself.
 *
 * <p>NCP (non-contributory period) days and refund-of-advances aren't tracked anywhere in the
 * statutory engine yet, so both are emitted as {@code 0} rather than guessed — a filer must correct
 * these two columns by hand until LOP days are threaded through {@link
 * com.ewos.payroll.application.StatutoryDeductionService}.
 */
@Component
public final class PfEcrFileExporter {

    private static final String DELIMITER = "#~#";
    private static final String NEWLINE = "\r\n";

    /** One member row on the ECR file. */
    public record PfEcrRow(
            String uan,
            String memberName,
            BigDecimal grossWages,
            BigDecimal epfWages,
            BigDecimal epsWages,
            BigDecimal edliWages,
            BigDecimal epfContributionRemitted,
            BigDecimal epsContributionRemitted,
            BigDecimal epfEmployeeContributionRemitted) {}

    public String export(List<PfEcrRow> rows) {
        StringBuilder sb = new StringBuilder(128 + rows.size() * 96);
        for (PfEcrRow r : rows) {
            sb.append(nullSafe(r.uan()))
                    .append(DELIMITER)
                    .append(nullSafe(r.memberName()))
                    .append(DELIMITER)
                    .append(plain(r.grossWages()))
                    .append(DELIMITER)
                    .append(plain(r.epfWages()))
                    .append(DELIMITER)
                    .append(plain(r.epsWages()))
                    .append(DELIMITER)
                    .append(plain(r.edliWages()))
                    .append(DELIMITER)
                    .append(plain(r.epfContributionRemitted()))
                    .append(DELIMITER)
                    .append(plain(r.epsContributionRemitted()))
                    .append(DELIMITER)
                    .append(plain(r.epfEmployeeContributionRemitted()))
                    .append(DELIMITER)
                    .append(0) // NCP days — not tracked yet, see class javadoc.
                    .append(DELIMITER)
                    .append(plain(BigDecimal.ZERO)) // Refund of advances — not tracked yet.
                    .append(NEWLINE);
        }
        return sb.toString();
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String plain(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).toPlainString();
    }
}
