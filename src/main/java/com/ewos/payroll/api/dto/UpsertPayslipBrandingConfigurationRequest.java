package com.ewos.payroll.api.dto;

import com.ewos.payroll.domain.PayslipPasswordPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpsertPayslipBrandingConfigurationRequest(
        @NotNull UUID tenantId,
        @NotNull UUID companyId,
        @NotBlank @Size(max = 255) String displayName,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 255) String supportEmail,
        @Size(max = 1000) String footerNote,
        @Size(max = 2000) String logoStorageUri,
        @NotNull PayslipPasswordPolicy passwordPolicy) {}
