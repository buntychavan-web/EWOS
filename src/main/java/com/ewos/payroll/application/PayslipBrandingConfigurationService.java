package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.PayslipBrandingConfigurationResponse;
import com.ewos.payroll.api.dto.UpsertPayslipBrandingConfigurationRequest;
import com.ewos.payroll.domain.PayslipBrandingConfiguration;
import com.ewos.payroll.infrastructure.persistence.PayslipBrandingConfigurationRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for a company's payslip PDF branding (Sprint 24K item 3). */
@Service
@Transactional
public class PayslipBrandingConfigurationService {

    private final PayslipBrandingConfigurationRepository repository;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public PayslipBrandingConfigurationService(
            PayslipBrandingConfigurationRepository repository,
            PayrollMapper mapper,
            ClientAccessGuard guard) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
    }

    /** Creates the company's branding configuration, or updates it if one already exists. */
    public PayslipBrandingConfigurationResponse upsert(
            UpsertPayslipBrandingConfigurationRequest request) {
        guard.requireAccessForCompany(request.companyId());
        PayslipBrandingConfiguration c =
                repository
                        .findByTenantIdAndCompanyIdAndActiveTrue(
                                request.tenantId(), request.companyId())
                        .orElseGet(PayslipBrandingConfiguration::new);
        c.setTenantId(request.tenantId());
        c.setCompanyId(request.companyId());
        c.setDisplayName(request.displayName());
        c.setAddressLine1(request.addressLine1());
        c.setAddressLine2(request.addressLine2());
        c.setSupportEmail(request.supportEmail());
        c.setFooterNote(request.footerNote());
        c.setLogoStorageUri(request.logoStorageUri());
        c.setPasswordPolicy(request.passwordPolicy());
        return mapper.toResponse(repository.save(c));
    }

    @Transactional(readOnly = true)
    public PayslipBrandingConfigurationResponse getForCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return repository
                .findByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId)
                .map(mapper::toResponse)
                .orElse(null);
    }
}
