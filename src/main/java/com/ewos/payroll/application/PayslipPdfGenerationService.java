package com.ewos.payroll.application;

import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipBrandingConfiguration;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.PayslipPasswordPolicy;
import com.ewos.shared.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Renders a {@link Payslip} to PDF (Sprint 24K item 3). Pure rendering — no persistence, no network
 * calls; the caller decides whether to stream the result directly or bundle several into a ZIP for
 * bulk download. Uses PDFBox's low-level content-stream API directly rather than an HTML/CSS
 * templating layer, keeping the dependency footprint to the one library.
 *
 * <p>Employer branding ({@link PayslipBrandingConfiguration}) is text-only — see that class's
 * javadoc for why a logo image is not rendered yet. Password protection ({@link #generate}'s {@code
 * password} parameter) uses PDFBox's standard AES encryption when a non-blank password is supplied;
 * the caller resolves the actual password string from the company's configured {@link
 * PayslipPasswordPolicy} (see {@code PayslipPdfService}) rather than this class assuming a
 * convention.
 */
@Service
public class PayslipPdfGenerationService {

    private static final float MARGIN = 50f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDFont FONT_BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * Renders one payslip. If {@code password} is non-blank, the PDF is AES-256 encrypted so it
     * cannot be opened without it (the generated document's owner password is random and discarded
     * — this service supports only the "user must supply a password to view" use case, not
     * owner-level permission editing after the fact).
     */
    public byte[] generate(
            Payslip payslip, PayslipBrandingConfiguration branding, String password) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = PAGE_HEIGHT - MARGIN;
                y = writeHeader(content, branding, y);
                y = writeEmployeeBlock(content, payslip, y);
                y = writeLinesTable(content, payslip, PayComponentKind.EARNING, "Earnings", y);
                y = writeLinesTable(content, payslip, PayComponentKind.DEDUCTION, "Deductions", y);
                y = writeSummary(content, payslip, y);
                writeFooter(content, branding, y);
            }

            if (password != null && !password.isBlank()) {
                protectWithPassword(document, password);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate payslip PDF", e);
        }
    }

    /** Resolves the actual password string for a company's configured policy, or null for none. */
    public String resolvePassword(Payslip payslip, PayslipBrandingConfiguration branding) {
        if (branding == null || branding.getPasswordPolicy() == null) {
            return null;
        }
        var employee = payslip.getEmployee();
        return switch (branding.getPasswordPolicy()) {
            case NONE -> null;
            case EMPLOYEE_NUMBER -> payslip.getEmployeeNumberSnapshot();
            case DATE_OF_BIRTH_DDMMYYYY -> {
                if (employee == null || employee.getDateOfBirth() == null) {
                    throw new ApiException(
                            HttpStatus.CONFLICT,
                            "Employee has no date of birth on file; cannot apply the configured"
                                    + " DATE_OF_BIRTH_DDMMYYYY password policy");
                }
                yield employee.getDateOfBirth().format(DateTimeFormatter.ofPattern("ddMMyyyy"));
            }
        };
    }

    private static float writeHeader(
            PDPageContentStream content, PayslipBrandingConfiguration branding, float y)
            throws IOException {
        String displayName = branding != null ? branding.getDisplayName() : "Payslip";
        content.beginText();
        content.setFont(FONT_BOLD, 16);
        content.newLineAtOffset(MARGIN, y);
        content.showText(displayName);
        content.endText();
        y -= 18;

        if (branding != null) {
            for (String line :
                    List.of(
                            nullToEmpty(branding.getAddressLine1()),
                            nullToEmpty(branding.getAddressLine2()))) {
                if (line.isEmpty()) {
                    continue;
                }
                content.beginText();
                content.setFont(FONT_REGULAR, 9);
                content.newLineAtOffset(MARGIN, y);
                content.showText(line);
                content.endText();
                y -= 12;
            }
        }
        y -= 10;
        content.beginText();
        content.setFont(FONT_BOLD, 13);
        content.newLineAtOffset(MARGIN, y);
        content.showText("Payslip");
        content.endText();
        return y - 20;
    }

    private static float writeEmployeeBlock(PDPageContentStream content, Payslip payslip, float y)
            throws IOException {
        String[][] rows = {
            {"Employee Name", nullToEmpty(payslip.getEmployeeNameSnapshot())},
            {"Employee Number", nullToEmpty(payslip.getEmployeeNumberSnapshot())},
            {
                "Pay Period",
                formatDate(payslip.getPeriodStart()) + " to " + formatDate(payslip.getPeriodEnd())
            },
            {"Pay Date", formatDate(payslip.getPayDate())},
            {"Currency", nullToEmpty(payslip.getCurrency())}
        };
        for (String[] row : rows) {
            content.beginText();
            content.setFont(FONT_BOLD, 10);
            content.newLineAtOffset(MARGIN, y);
            content.showText(row[0] + ":");
            content.endText();
            content.beginText();
            content.setFont(FONT_REGULAR, 10);
            content.newLineAtOffset(MARGIN + 150, y);
            content.showText(row[1]);
            content.endText();
            y -= 14;
        }
        return y - 12;
    }

    private static float writeLinesTable(
            PDPageContentStream content,
            Payslip payslip,
            PayComponentKind kind,
            String heading,
            float y)
            throws IOException {
        List<PayslipLine> lines =
                payslip.getLines().stream()
                        .filter(l -> l.getKind() == kind)
                        .sorted(Comparator.comparingInt(PayslipLine::getSortOrder))
                        .toList();

        content.beginText();
        content.setFont(FONT_BOLD, 11);
        content.newLineAtOffset(MARGIN, y);
        content.showText(heading);
        content.endText();
        y -= 16;

        for (PayslipLine line : lines) {
            content.beginText();
            content.setFont(FONT_REGULAR, 10);
            content.newLineAtOffset(MARGIN + 10, y);
            content.showText(nullToEmpty(line.getComponentNameSnapshot()));
            content.endText();

            String amountText = formatAmount(line.getAmount());
            content.beginText();
            content.setFont(FONT_REGULAR, 10);
            content.newLineAtOffset(
                    PAGE_WIDTH - MARGIN - textWidth(FONT_REGULAR, 10, amountText), y);
            content.showText(amountText);
            content.endText();
            y -= 14;
        }
        return y - 14;
    }

    private static float writeSummary(PDPageContentStream content, Payslip payslip, float y)
            throws IOException {
        String[][] rows = {
            {"Gross Earnings", formatAmount(payslip.getGrossAmount())},
            {"Total Deductions", formatAmount(payslip.getDeductionsAmount())},
            {"Net Pay", formatAmount(payslip.getNetAmount())}
        };
        for (String[] row : rows) {
            content.beginText();
            content.setFont(FONT_BOLD, 11);
            content.newLineAtOffset(MARGIN, y);
            content.showText(row[0] + ":");
            content.endText();
            content.beginText();
            content.setFont(FONT_BOLD, 11);
            content.newLineAtOffset(PAGE_WIDTH - MARGIN - textWidth(FONT_BOLD, 11, row[1]), y);
            content.showText(row[1]);
            content.endText();
            y -= 16;
        }
        return y - 10;
    }

    private static void writeFooter(
            PDPageContentStream content, PayslipBrandingConfiguration branding, float y)
            throws IOException {
        if (branding == null) {
            return;
        }
        if (branding.getSupportEmail() != null && !branding.getSupportEmail().isBlank()) {
            content.beginText();
            content.setFont(FONT_REGULAR, 8);
            content.newLineAtOffset(MARGIN, MARGIN);
            content.showText("Support: " + branding.getSupportEmail());
            content.endText();
        }
        if (branding.getFooterNote() != null && !branding.getFooterNote().isBlank()) {
            content.beginText();
            content.setFont(FONT_REGULAR, 8);
            content.newLineAtOffset(MARGIN, MARGIN - 12);
            content.showText(branding.getFooterNote());
            content.endText();
        }
    }

    private static void protectWithPassword(PDDocument document, String userPassword)
            throws IOException {
        String ownerPassword = randomOwnerPassword();
        AccessPermission permissions = new AccessPermission();
        permissions.setCanPrint(true);
        permissions.setCanExtractContent(false);
        permissions.setCanModify(false);
        StandardProtectionPolicy policy =
                new StandardProtectionPolicy(ownerPassword, userPassword, permissions);
        policy.setEncryptionKeyLength(256);
        document.protect(policy);
    }

    private static String randomOwnerPassword() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    private static float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    private static String formatAmount(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMAT);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
