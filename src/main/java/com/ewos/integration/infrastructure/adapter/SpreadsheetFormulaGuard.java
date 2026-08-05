package com.ewos.integration.infrastructure.adapter;

/**
 * CSV/Excel formula-injection mitigation for {@link CsvIntegrationAdapter} and {@link
 * ExcelIntegrationAdapter}: both write an exchange record's {@code payloadJson} — business data
 * that can originate from a client-supplied {@code DataExchangeController.create} request body —
 * directly into a spreadsheet cell with no restriction on its content. If a value's first character
 * is one Excel treats as a formula prefix ({@code = + - @}, or a leading tab/carriage return), an
 * operator opening the exported file in Excel is exposed to formula injection/execution. Prefixing
 * a single quote forces text interpretation without altering the value's actual content — cell
 * display in every spreadsheet application simply drops a leading {@code '} used this way.
 *
 * <p>Not applied anywhere numeric values are expected to legitimately start with {@code -} or
 * {@code +} (there are none among this module's export columns — {@code correlationId}, {@code
 * exchangeType}, and {@code payloadJson} are always identifiers or JSON text).
 */
final class SpreadsheetFormulaGuard {

    private static final String DANGEROUS_PREFIXES = "=+-@\t\r";

    private SpreadsheetFormulaGuard() {}

    static String neutralize(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return DANGEROUS_PREFIXES.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
