package com.ewos.integration.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class XlsxWriterTest {

    @Test
    void producesAValidZipWithTheFiveRequiredOoxmlParts() throws IOException {
        byte[] bytes =
                XlsxWriter.write(List.of(List.of("Header1", "Header2"), List.of("value & <1>", "v2")));

        List<String> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }

        assertThat(entries)
                .containsExactlyInAnyOrder(
                        "[Content_Types].xml",
                        "_rels/.rels",
                        "xl/workbook.xml",
                        "xl/_rels/workbook.xml.rels",
                        "xl/worksheets/sheet1.xml");
    }

    @Test
    void escapesXmlSpecialCharactersInCellValues() throws IOException {
        byte[] bytes = XlsxWriter.write(List.of(List.of("a & b < c > d \" e")));

        String sheetXml = readEntry(bytes, "xl/worksheets/sheet1.xml");

        assertThat(sheetXml).contains("a &amp; b &lt; c &gt; d &quot; e");
        assertThat(sheetXml).doesNotContain("a & b < c > d \" e");
    }

    private static String readEntry(byte[] zipBytes, String entryName) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    return new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("Entry not found: " + entryName);
    }
}
