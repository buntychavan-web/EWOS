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
}
