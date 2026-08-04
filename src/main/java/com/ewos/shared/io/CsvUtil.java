package com.ewos.shared.io;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprint 24E — shared RFC-4180 CSV read/write helpers for the import/export utilities introduced
 * across Goals, Competency, and Development Plans. Mirrors the escaping convention already used by
 * {@code com.ewos.payroll.domain.RegisterCsvExporter} and friends, extended with a matching line
 * parser since those export-only classes never needed one.
 */
public final class CsvUtil {

    private CsvUtil() {}

    /** Quotes a field only if it contains a comma, quote, or newline; doubles embedded quotes. */
    public static String escape(String v) {
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

    /** Splits the raw file body into non-blank, trimmed-of-trailing-CR lines. */
    public static List<String> splitLines(String body) {
        List<String> lines = new ArrayList<>();
        if (body == null) {
            return lines;
        }
        for (String raw : body.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * Parses one RFC-4180 CSV line into fields, honoring quoted fields (with embedded commas,
     * newlines are not supported within a field since input is pre-split by line) and doubled-quote
     * escaping.
     */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        int len = line.length();
        while (i < len) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
            i++;
        }
        fields.add(current.toString());
        return fields;
    }

    public static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
