package com.ewos.exit.application;

import com.ewos.shared.document.EmbeddedFonts;
import com.ewos.shared.document.PdfFontLoader;
import com.ewos.shared.document.PdfTextLayout;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.springframework.stereotype.Service;

/**
 * Renders a letter (acceptance/relieving/experience/service-certificate/F&F-statement) to PDF —
 * pure rendering, no persistence, same PDFBox content-stream approach as {@code
 * PayslipPdfGenerationService} rather than pulling in an HTML/CSS templating dependency. Simpler
 * than the payslip renderer: one title, one word-wrapped body paragraph, no tabular data.
 *
 * <p>Uses an embedded GNU FreeSans (see {@code src/main/resources/fonts/README.md}) rather than a
 * Standard-14 font (Sprint 26A P1-3): Standard-14 fonts use a single-byte WinAnsiEncoding and throw
 * when asked to render a character outside it — Devanagari and most other non-Latin-1 text included
 * — which made this service unsuitable for Indian names and other multilingual letter content.
 * FreeSans is a full Unicode TrueType font; PDFBox embeds only the glyph subset actually used, so
 * output size stays close to what the Standard-14 fonts produced. Font loading and text
 * measurement/wrapping are shared with {@code PayslipPdfGenerationService} via {@code
 * com.ewos.shared.document} (Sprint 27A, audit finding 8.2 — this class previously duplicated
 * both).
 */
@Service
public class ExitDocumentPdfGenerationService {

    private static final float MARGIN = 60f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float BODY_FONT_SIZE = 11f;
    private static final float LINE_HEIGHT = 16f;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    /** Renders one letter. {@code body} is the already token-substituted plain text. */
    public byte[] generate(String title, String body, LocalDate issueDate) {
        try (PDDocument document = new PDDocument()) {
            PDFont fontRegular =
                    PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_REGULAR);
            PDFont fontBold =
                    PdfFontLoader.loadEmbeddedFont(document, EmbeddedFonts.FREE_SANS_BOLD);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT - MARGIN;
                y = writeTitle(content, fontBold, title, y);
                y = writeIssueDate(content, fontRegular, issueDate, y);
                writeBody(content, fontRegular, body, y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate exit document PDF", e);
        }
    }

    private static float writeTitle(
            PDPageContentStream content, PDFont fontBold, String title, float startY)
            throws IOException {
        content.beginText();
        content.setFont(fontBold, 16);
        content.newLineAtOffset(MARGIN, startY);
        content.showText(title == null ? "" : title);
        content.endText();
        return startY - 24;
    }

    private static float writeIssueDate(
            PDPageContentStream content, PDFont fontRegular, LocalDate issueDate, float startY)
            throws IOException {
        content.beginText();
        content.setFont(fontRegular, 10);
        content.newLineAtOffset(MARGIN, startY);
        content.showText("Date: " + (issueDate == null ? "" : issueDate.format(DATE_FORMAT)));
        content.endText();
        return startY - 28;
    }

    private static void writeBody(
            PDPageContentStream content, PDFont fontRegular, String body, float startY)
            throws IOException {
        float y = startY;
        float maxWidth = PAGE_WIDTH - 2 * MARGIN;
        for (String paragraph : (body == null ? "" : body).split("\n", -1)) {
            for (String line :
                    PdfTextLayout.wrap(fontRegular, paragraph, BODY_FONT_SIZE, maxWidth)) {
                if (y < MARGIN) {
                    return;
                }
                content.beginText();
                content.setFont(fontRegular, BODY_FONT_SIZE);
                content.newLineAtOffset(MARGIN, y);
                content.showText(line);
                content.endText();
                y -= LINE_HEIGHT;
            }
            y -= LINE_HEIGHT / 2;
        }
    }
}
