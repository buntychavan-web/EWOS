package com.ewos.payroll.application;

import java.util.UUID;

/**
 * Extension point for digitally signing a generated payslip PDF (Sprint 24K item 3 — "digital
 * signature architecture", deliberately architecture only). Real PKI signing needs a company-held
 * signing certificate/private key (typically an HSM or a protected keystore) that cannot be
 * invented or hardcoded here — provisioning one is an infrastructure/compliance decision for each
 * deployment, not something this sprint can safely assume. {@link NoOpPayslipSignatureService} is
 * the default bean until a real certificate-backed implementation is registered; a production
 * implementation would use PDFBox's built-in {@code PDDocument#addSignature} / {@code
 * SignatureInterface} support against a keystore resolved per tenant/company, without any change to
 * callers of this interface.
 */
public interface PayslipSignatureService {

    /** Returns the signed PDF bytes, or the input unchanged if no signing is configured. */
    byte[] sign(byte[] unsignedPdf, UUID tenantId, UUID companyId);

    /** Whether a real signing certificate is configured for this tenant/company. */
    boolean isConfigured(UUID tenantId, UUID companyId);
}
