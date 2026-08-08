package com.ewos.shared.document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.font.PDFont;

/**
 * Text-measurement and word-wrap helpers shared by the platform's PDFBox-based document generators
 * (originally {@code ExitDocumentPdfGenerationService}'s private {@code wrap}/{@code textWidth},
 * extracted in Sprint 27A — audit finding 8.2 — and now also used by {@code
 * PayslipPdfGenerationService} once it moved off Standard-14 fonts). Pure functions: no state, no
 * PDFBox document/content-stream side effects.
 */
public final class PdfTextLayout {

    private PdfTextLayout() {}

    /**
     * Width, in PDF user-space units, of {@code text} rendered in {@code font} at {@code fontSize}.
     */
    public static float textWidth(PDFont font, String text, float fontSize) throws IOException {
        return font.getStringWidth(text == null ? "" : text) / 1000f * fontSize;
    }

    /**
     * Greedily word-wraps {@code paragraph} to fit within {@code maxWidth}. A blank paragraph
     * yields a single empty line (preserving intentional blank lines in multi-paragraph bodies)
     * rather than zero lines.
     */
    public static List<String> wrap(PDFont font, String paragraph, float fontSize, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (paragraph == null || paragraph.isBlank()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textWidth(font, candidate, fontSize) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }
}
