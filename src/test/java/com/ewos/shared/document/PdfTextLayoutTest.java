package com.ewos.shared.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

class PdfTextLayoutTest {

    @Test
    void wrapKeepsAShortParagraphOnOneLine() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_REGULAR);

            List<String> lines = PdfTextLayout.wrap(font, "Thank you for your service.", 11f, 500f);

            assertThat(lines).containsExactly("Thank you for your service.");
        }
    }

    @Test
    void wrapBreaksALongParagraphAcrossMultipleLinesThatAllFitTheWidth() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_REGULAR);
            float maxWidth = 150f;

            List<String> lines = PdfTextLayout.wrap(font, "word ".repeat(60).trim(), 11f, maxWidth);

            assertThat(lines).hasSizeGreaterThan(1);
            for (String line : lines) {
                assertThat(PdfTextLayout.textWidth(font, line, 11f)).isLessThanOrEqualTo(maxWidth);
            }
        }
    }

    @Test
    void wrapOfABlankParagraphReturnsOneEmptyLine() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_REGULAR);

            assertThat(PdfTextLayout.wrap(font, "", 11f, 500f)).containsExactly("");
            assertThat(PdfTextLayout.wrap(font, null, 11f, 500f)).containsExactly("");
        }
    }

    @Test
    void textWidthOfNullIsZero() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_REGULAR);

            assertThat(PdfTextLayout.textWidth(font, null, 11f)).isZero();
        }
    }

    @Test
    void textWidthScalesWithFontSize() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_REGULAR);

            float small = PdfTextLayout.textWidth(font, "Payslip", 10f);
            float large = PdfTextLayout.textWidth(font, "Payslip", 20f);

            assertThat(large).isEqualTo(small * 2f, org.assertj.core.data.Offset.offset(0.01f));
        }
    }
}
