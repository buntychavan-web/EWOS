package com.ewos.shared.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.junit.jupiter.api.Test;

class PdfFontLoaderTest {

    @Test
    void loadsTheRegularAndBoldEmbeddedFonts() throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont regular =
                    PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_REGULAR);
            PDFont bold = PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_BOLD);

            assertThat(regular).isNotNull();
            assertThat(bold).isNotNull();
            // Full Unicode TrueType, not a Standard-14 single-byte font — the whole point of this
            // utility (see class javadoc / Sprint 26A P1-3 / Sprint 27A FR-11).
            assertThat(regular.getStringWidth("प्रियांका")).isGreaterThan(0);
        }
    }

    @Test
    void throwsAClearErrorForAMissingResource() throws IOException {
        try (PDDocument document = new PDDocument()) {
            assertThatThrownBy(
                            () ->
                                    PdfFontLoader.loadEmbeddedFont(
                                            document, "/fonts/DoesNotExist.ttf"))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("DoesNotExist.ttf");
        }
    }
}
