package com.ewos.payroll.application;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Default {@link PayslipSignatureService}: no signing certificate is provisioned anywhere yet, so
 * this returns the PDF unmodified. Swap in a real certificate-backed implementation (see {@link
 * PayslipSignatureService}'s javadoc) once a tenant/company has one; every caller of this interface
 * needs no change when that happens.
 */
@Service
public class NoOpPayslipSignatureService implements PayslipSignatureService {

    @Override
    public byte[] sign(byte[] unsignedPdf, UUID tenantId, UUID companyId) {
        return unsignedPdf;
    }

    @Override
    public boolean isConfigured(UUID tenantId, UUID companyId) {
        return false;
    }
}
