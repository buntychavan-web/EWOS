package com.ewos.exit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ExitDocumentPdfGenerationServiceTest {

    private final ExitDocumentPdfGenerationService service = new ExitDocumentPdfGenerationService();

    @Test
    void generateProducesANonEmptyPdf() {
        byte[] pdf =
                service.generate(
                        "Relieving Letter",
                        "Dear Jordan,\n\nThank you for your service.",
                        LocalDate.now());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
    }

    @Test
    void generateWrapsALongBodyWithoutThrowing() {
        String longWord = "word".repeat(50);
        String longBody = (longWord + " ").repeat(200);

        byte[] pdf = service.generate("Long Letter", longBody, LocalDate.now());

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generateHandlesNullTitleAndBody() {
        byte[] pdf = service.generate(null, null, null);

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generateRendersIndianNamesAndDevanagariTextWithoutThrowing() {
        // Sprint 26A P1-3: the previous Standard-14 Helvetica font used a single-byte
        // WinAnsiEncoding and threw IllegalArgumentException for any character outside it —
        // including every Devanagari code point. This must now render cleanly.
        byte[] pdf =
                service.generate(
                        "Relieving Letter",
                        "Dear Priyāṅkā Śrīvāstava (प्रियांका श्रीवास्तव),\n\n"
                                + "आपकी सेवा के लिए धन्यवाद। We wish you the very best, Rāghavendra"
                                + " Iyer.",
                        LocalDate.now());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
    }
}
