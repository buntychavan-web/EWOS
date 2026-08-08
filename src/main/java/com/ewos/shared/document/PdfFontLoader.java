package com.ewos.shared.document;

import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Loads an embedded, full-Unicode {@link PDType0Font} from a classpath resource (originally {@code
 * ExitDocumentPdfGenerationService}'s private {@code loadFont}, extracted in Sprint 27A — audit
 * finding 8.2 — so every PDF generator in the platform shares one implementation instead of each
 * module re-implementing it).
 *
 * <p><strong>{@link PDType0Font} instances are bound to the one {@link PDDocument} they were loaded
 * into and must never be cached or shared across documents</strong> — unlike PDFBox's Standard-14
 * fonts ({@code PDType1Font}), which are stateless and safe as {@code static} fields. Callers must
 * call this once per {@link PDDocument} (typically at the start of the document's own {@code
 * generate(...)} method) and thread the returned {@link PDFont} through as a parameter, not a
 * shared field.
 */
public final class PdfFontLoader {

    private PdfFontLoader() {}

    /**
     * @param document the document this font will be embedded into; the returned font is only valid
     *     for this document
     * @param classpathResource e.g. {@link EmbeddedFonts#FREE_SANS_REGULAR}
     */
    public static PDFont loadEmbeddedFont(PDDocument document, String classpathResource)
            throws IOException {
        try (InputStream in = PdfFontLoader.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IOException("Embedded font resource not found: " + classpathResource);
            }
            return PDType0Font.load(document, in, true);
        }
    }
}
