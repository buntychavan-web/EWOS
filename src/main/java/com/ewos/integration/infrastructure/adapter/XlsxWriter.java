package com.ewos.integration.infrastructure.adapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Hand-rolled minimal valid {@code .xlsx} (ECMA-376 SpreadsheetML) writer — no Apache POI, which
 * isn't available in this environment's offline Maven cache (see Sprint 14.4 Implementation Report,
 * "Implementation Issues"). A {@code .xlsx} file is a ZIP of five small, well-known XML parts; this
 * class writes exactly those five parts with one worksheet, using inline strings (avoids needing a
 * {@code sharedStrings.xml} part). Real spreadsheet applications (Excel, Google Sheets,
 * LibreOffice) open the result without repair prompts — this is a standard technique for generating
 * simple tabular spreadsheets without a full OOXML library.
 */
public final class XlsxWriter {

    private XlsxWriter() {}

    public static byte[] write(List<List<String>> rows) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(buffer)) {
            writeEntry(zip, "[Content_Types].xml", contentTypesXml());
            writeEntry(zip, "_rels/.rels", rootRelsXml());
            writeEntry(zip, "xl/workbook.xml", workbookXml());
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            writeEntry(zip, "xl/worksheets/sheet1.xml", sheetXml(rows));
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write XLSX", e);
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content)
            throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\""
                + " ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\""
                + " ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    private static String rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\""
                + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\""
                + " Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbookXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\""
                + " Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\""
                + " Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>";
    }

    private static String sheetXml(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                        + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                        + "<sheetData>");
        int rowIndex = 1;
        for (List<String> row : rows) {
            sb.append("<row r=\"").append(rowIndex).append("\">");
            int colIndex = 0;
            for (String cell : row) {
                sb.append("<c r=\"")
                        .append(columnRef(colIndex))
                        .append(rowIndex)
                        .append("\" t=\"inlineStr\"><is><t>")
                        .append(escapeXml(SpreadsheetFormulaGuard.neutralize(cell)))
                        .append("</t></is></c>");
                colIndex++;
            }
            sb.append("</row>");
            rowIndex++;
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static String columnRef(int zeroBasedIndex) {
        StringBuilder ref = new StringBuilder();
        int n = zeroBasedIndex;
        do {
            ref.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return ref.toString();
    }

    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
