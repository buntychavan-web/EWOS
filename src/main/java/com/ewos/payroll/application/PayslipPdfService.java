package com.ewos.payroll.application;

import com.ewos.payroll.domain.Payslip;
import com.ewos.payroll.domain.PayslipBrandingConfiguration;
import com.ewos.payroll.infrastructure.persistence.PayslipBrandingConfigurationRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates payslip PDF generation (Sprint 24K item 3) for all three delivery paths — admin
 * individual download, admin bulk download (ZIP, one PDF per employee), and employee self-service
 * download — on top of {@link PayslipService} (access control), {@link PayslipPdfGenerationService}
 * (rendering), and {@link PayslipSignatureService} (architecture hook, currently a no-op). Nothing
 * here duplicates any of those — this class only wires them together per request.
 */
@Service
@Transactional(readOnly = true)
public class PayslipPdfService {

    private final PayslipService payslips;
    private final PayslipBrandingConfigurationRepository branding;
    private final PayslipPdfGenerationService generator;
    private final PayslipSignatureService signatureService;

    public PayslipPdfService(
            PayslipService payslips,
            PayslipBrandingConfigurationRepository branding,
            PayslipPdfGenerationService generator,
            PayslipSignatureService signatureService) {
        this.payslips = payslips;
        this.branding = branding;
        this.generator = generator;
        this.signatureService = signatureService;
    }

    /** Admin download of one payslip, gated by {@link PayslipService#requireForAdmin}. */
    public byte[] generateForAdmin(UUID tenantId, UUID payslipId) {
        Payslip payslip = payslips.requireForAdmin(tenantId, payslipId);
        return generate(payslip);
    }

    /** Self-service download of the caller's own payslip. */
    public byte[] generateForSelf(UUID tenantId, UUID employeeId, UUID payslipId) {
        Payslip payslip = payslips.requireOwn(tenantId, employeeId, payslipId);
        return generate(payslip);
    }

    /** Bulk admin download: every payslip on a run, as one PDF per employee inside a ZIP. */
    public byte[] generateZipForRun(UUID tenantId, UUID runId) {
        List<Payslip> slips = payslips.entitiesForRun(tenantId, runId);
        try (java.io.ByteArrayOutputStream byteOut = new java.io.ByteArrayOutputStream();
                ZipOutputStream zip = new ZipOutputStream(byteOut)) {
            for (Payslip slip : slips) {
                byte[] pdf = generate(slip);
                String filename = fileNameFor(slip);
                zip.putNextEntry(new ZipEntry(filename));
                zip.write(pdf);
                zip.closeEntry();
            }
            zip.finish();
            return byteOut.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build bulk payslip ZIP", e);
        }
    }

    private byte[] generate(Payslip payslip) {
        PayslipBrandingConfiguration config =
                branding.findByTenantIdAndCompanyIdAndActiveTrue(
                                payslip.getTenantId(), payslip.getCompanyId())
                        .orElse(null);
        String password = generator.resolvePassword(payslip, config);
        byte[] unsigned = generator.generate(payslip, config, password);
        return signatureService.sign(unsigned, payslip.getTenantId(), payslip.getCompanyId());
    }

    private static String fileNameFor(Payslip payslip) {
        String number =
                payslip.getEmployeeNumberSnapshot() != null
                        ? payslip.getEmployeeNumberSnapshot()
                        : payslip.getId().toString();
        String period = payslip.getPeriodStart() != null ? payslip.getPeriodStart().toString() : "";
        return ("payslip_" + number + "_" + period + ".pdf").replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
