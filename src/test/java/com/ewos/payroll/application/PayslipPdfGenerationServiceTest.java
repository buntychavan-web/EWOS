package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.employee.domain.Employee;
import com.ewos.payroll.domain.PayComponentCalculationType;
import com.ewos.payroll.domain.PayComponentKind;
import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipBrandingConfiguration;
import com.ewos.payroll.domain.PayslipLine;
import com.ewos.payroll.domain.PayslipPasswordPolicy;
import com.ewos.shared.exception.ApiException;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

/** Sprint 24K item 3 — payslip PDF rendering, password protection, and password resolution. */
class PayslipPdfGenerationServiceTest {

    private final PayslipPdfGenerationService service = new PayslipPdfGenerationService();

    private static Payslip payslip() {
        Payslip p = new Payslip();
        p.setEmployeeNameSnapshot("Asha Rao");
        p.setEmployeeNumberSnapshot("E100");
        p.setPeriodStart(LocalDate.of(2026, 8, 1));
        p.setPeriodEnd(LocalDate.of(2026, 8, 31));
        p.setPayDate(LocalDate.of(2026, 9, 1));
        p.setCurrency("INR");
        p.setGrossAmount(new BigDecimal("60000"));
        p.setDeductionsAmount(new BigDecimal("8000"));
        p.setNetAmount(new BigDecimal("52000"));

        PayslipLine basic = new PayslipLine();
        basic.setComponentCodeSnapshot("BASIC");
        basic.setComponentNameSnapshot("Basic Salary");
        basic.setKind(PayComponentKind.EARNING);
        basic.setCalculationType(PayComponentCalculationType.FIXED);
        basic.setAmount(new BigDecimal("60000"));
        basic.setSortOrder(0);
        p.addLine(basic);

        PayslipLine pf = new PayslipLine();
        pf.setComponentCodeSnapshot("STATUTORY_PF");
        pf.setComponentNameSnapshot("Provident Fund");
        pf.setKind(PayComponentKind.DEDUCTION);
        pf.setCalculationType(PayComponentCalculationType.STATUTORY_PF);
        pf.setAmount(new BigDecimal("8000"));
        pf.setSortOrder(1);
        p.addLine(pf);

        Employee e = new Employee();
        e.setDateOfBirth(LocalDate.of(1990, 3, 15));
        p.setEmployee(e);
        return p;
    }

    private static PayslipBrandingConfiguration branding(PayslipPasswordPolicy policy) {
        PayslipBrandingConfiguration b = new PayslipBrandingConfiguration();
        b.setDisplayName("Acme Corp");
        b.setAddressLine1("123 Business Park");
        b.setSupportEmail("payroll@acme.example");
        b.setFooterNote("This is a system-generated payslip.");
        b.setPasswordPolicy(policy);
        return b;
    }

    @Test
    void generateProducesAValidUnencryptedPdfByDefault() throws IOException {
        byte[] pdf = service.generate(payslip(), branding(PayslipPasswordPolicy.NONE), null);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
            assertThat(doc.isEncrypted()).isFalse();
        }
    }

    @Test
    void generateWithAPasswordProducesAnEncryptedPdfRequiringIt() throws IOException {
        byte[] pdf = service.generate(payslip(), branding(PayslipPasswordPolicy.NONE), "secret123");

        assertThatThrownBy(() -> Loader.loadPDF(pdf)).isInstanceOf(IOException.class);

        try (PDDocument doc = Loader.loadPDF(pdf, "secret123")) {
            assertThat(doc.isEncrypted()).isTrue();
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void resolvePasswordReturnsNullWhenPolicyIsNone() {
        assertThat(service.resolvePassword(payslip(), branding(PayslipPasswordPolicy.NONE)))
                .isNull();
    }

    @Test
    void resolvePasswordUsesEmployeeNumberWhenConfigured() {
        assertThat(
                        service.resolvePassword(
                                payslip(), branding(PayslipPasswordPolicy.EMPLOYEE_NUMBER)))
                .isEqualTo("E100");
    }

    @Test
    void resolvePasswordUsesDateOfBirthWhenConfigured() {
        assertThat(
                        service.resolvePassword(
                                payslip(), branding(PayslipPasswordPolicy.DATE_OF_BIRTH_DDMMYYYY)))
                .isEqualTo("15031990");
    }

    @Test
    void resolvePasswordRejectsDateOfBirthPolicyWhenEmployeeHasNoDateOfBirth() {
        Payslip p = payslip();
        p.getEmployee().setDateOfBirth(null);

        assertThatThrownBy(
                        () ->
                                service.resolvePassword(
                                        p, branding(PayslipPasswordPolicy.DATE_OF_BIRTH_DDMMYYYY)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void generateRendersIndianNamesAndDevanagariTextWithoutThrowing() throws IOException {
        // Sprint 27A hotfix (audit finding 1.5): this service previously used PDFBox's Standard-14
        // Helvetica fonts, which use a single-byte WinAnsiEncoding and throw
        // IllegalArgumentException for any character outside it — the same bug Sprint 26A P1-3
        // fixed for ExitDocumentPdfGenerationService. Must now render cleanly, mirroring that
        // service's regression test.
        Payslip p = payslip();
        p.setEmployeeNameSnapshot("Priyāṅkā Śrīvāstava (प्रियांका श्रीवास्तव)");
        PayslipBrandingConfiguration branding = branding(PayslipPasswordPolicy.NONE);
        branding.setDisplayName("Rāghavendra Iyer Enterprises Pvt. Ltd.");
        branding.setFooterNote("धन्यवाद — thank you for your service.");

        byte[] pdf = service.generate(p, branding, null);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII))
                .isEqualTo("%PDF-");
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }
}
