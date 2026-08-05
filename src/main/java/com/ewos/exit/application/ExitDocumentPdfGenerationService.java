package com.ewos.exit.application;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

/**
 * Renders a letter (acceptance/relieving/experience/service-certificate/F&F-statement) to PDF —
 * pure rendering, no persistence, same PDFBox content-stream approach as {@code
 * PayslipPdfGenerationService} rather than pulling in an HTML/CSS templating dependency. Simpler
 * than the payslip renderer: one title, one word-wrapped body paragraph, no tabular data.
 */
@Service
public class ExitDocumentPdfGenerationService {

    private static final float MARGIN = 60f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float BODY_FONT_SIZE = 11f;
    private static final float LINE_HEIGHT = 16f;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** Renders one letter. {@code body} is the already token-substituted plain text. */
    public byte[] generate(String title, String body, LocalDate issueDate) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT - MARGIN;
                y = writeTitle(content, title, y);
                y = writeIssueDate(content, issueDate, y);
                writeBody(content, body, y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate exit document PDF", e);
        }
    }

    private static float writeTitle(PDPageContentStream content, String title, float startY)
            throws IOException {
        content.beginText();
        content.setFont(FONT_BOLD, 16);
        content.newLineAtOffset(MARGIN, startY);
        content.showText(title == null ? "" : title);
        content.endText();
        return startY - 24;
    }

    private static float writeIssueDate(
            PDPageContentStream content, LocalDate issueDate, float startY) throws IOException {
        content.beginText();
        content.setFont(FONT_REGULAR, 10);
        content.newLineAtOffset(MARGIN, startY);
        content.showText("Date: " + (issueDate == null ? "" : issueDate.format(DATE_FORMAT)));
        content.endText();
        return startY - 28;
    }

    private static void writeBody(PDPageContentStream content, String body, float startY)
            throws IOException {
        float y = startY;
        float maxWidth = PAGE_WIDTH - 2 * MARGIN;
        for (String paragraph : (body == null ? "" : body).split("\n", -1)) {
            for (String line : wrap(paragraph, maxWidth)) {
                if (y < MARGIN) {
                    return;
                }
                content.beginText();
                content.setFont(FONT_REGULAR, BODY_FONT_SIZE);
                content.newLineAtOffset(MARGIN, y);
                content.showText(line);
                content.endText();
                y -= LINE_HEIGHT;
            }
            y -= LINE_HEIGHT / 2;
        }
    }

    private static List<String> wrap(String paragraph, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (paragraph.isBlank()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : paragraph.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textWidth(candidate) > maxWidth && !current.isEmpty()) {
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

    private static float textWidth(String text) throws IOException {
        return FONT_REGULAR.getStringWidth(text) / 1000f * BODY_FONT_SIZE;
    }
}
