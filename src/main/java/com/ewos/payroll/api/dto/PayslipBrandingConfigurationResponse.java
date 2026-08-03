package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayslipPasswordPolicy;
import java.util.UUID;

public record PayslipBrandingConfigurationResponse(
        UUID id,
        UUID tenantId,
        UUID companyId,
        String displayName,
        String addressLine1,
        String addressLine2,
        String supportEmail,
        String footerNote,
        String logoStorageUri,
        PayslipPasswordPolicy passwordPolicy,
        boolean active) {}
