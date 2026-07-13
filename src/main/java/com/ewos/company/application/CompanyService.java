package com.ewos.company.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.api.dto.CompanyProfile;
import com.ewos.company.api.dto.CompanyResponse;
import com.ewos.company.api.dto.CompanyVersionResponse;
import com.ewos.company.api.dto.CreateCompanyRequest;
import com.ewos.company.api.dto.UpdateCompanyProfileRequest;
import com.ewos.company.domain.Company;
import com.ewos.company.domain.CompanyPolicyAssignment;
import com.ewos.company.domain.CompanyVersion;
import com.ewos.company.domain.PolicyType;
import com.ewos.company.domain.Tenant;
import com.ewos.company.infrastructure.persistence.CompanyPolicyAssignmentRepository;
import com.ewos.company.infrastructure.persistence.CompanyRepository;
import com.ewos.company.infrastructure.persistence.CompanySpecifications;
import com.ewos.company.infrastructure.persistence.CompanyVersionRepository;
import com.ewos.company.infrastructure.persistence.TenantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core company lifecycle: create (blank + clone), read, effective-dated updates, status, soft
 * delete, search.
 */
@Service
@Transactional
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final CompanyVersionRepository versionRepository;
    private final CompanyPolicyAssignmentRepository policyAssignmentRepository;
    private final TenantRepository tenantRepository;

    public CompanyService(
            CompanyRepository companyRepository,
            CompanyVersionRepository versionRepository,
            CompanyPolicyAssignmentRepository policyAssignmentRepository,
            TenantRepository tenantRepository) {
        this.companyRepository = companyRepository;
        this.versionRepository = versionRepository;
        this.policyAssignmentRepository = policyAssignmentRepository;
        this.tenantRepository = tenantRepository;
    }

    public CompanyResponse create(CreateCompanyRequest request) {
        Tenant tenant = resolveTenant(request.tenantId());
        if (companyRepository.existsByTenantAndCode(tenant, request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Company code '" + request.code() + "' already exists for this tenant");
        }

        Company company = new Company();
        company.setTenant(tenant);
        company.setCode(request.code());
        company.setActive(true);
        Company saved = companyRepository.save(company);

        CompanyVersion initial = openNewVersion(saved, request.profile());

        // Clone mode: carry over selected policy assignments as new references starting from
        // the profile's effectiveFrom date. The source's assignments are left untouched.
        if (request.cloneFromId() != null) {
            Company source =
                    companyRepository
                            .findById(request.cloneFromId())
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Source company for clone not found"));
            clonePolicyAssignments(
                    source, saved, request.clonePolicyTypes(), request.profile().effectiveFrom());
        }

        return CompanyMapper.toCompany(saved, initial);
    }

    public CompanyResponse updateProfile(UUID companyId, UpdateCompanyProfileRequest request) {
        Company company = requireCompany(companyId);
        CompanyProfile profile = request.profile();
        EffectiveDateValidator.requireOrdered(profile.effectiveFrom(), null);

        // Close the currently open version (if any) the day before the new profile starts.
        versionRepository
                .findByCompanyAndEffectiveToIsNull(company)
                .ifPresent(
                        current -> {
                            LocalDate closeAt = profile.effectiveFrom().minusDays(1);
                            if (closeAt.isBefore(current.getEffectiveFrom())) {
                                throw new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "New profile effectiveFrom ("
                                                + profile.effectiveFrom()
                                                + ") would overlap the current version starting "
                                                + current.getEffectiveFrom());
                            }
                            current.setEffectiveTo(closeAt);
                        });

        CompanyVersion next = openNewVersion(company, profile);
        return CompanyMapper.toCompany(company, next);
    }

    public CompanyResponse setStatus(UUID companyId, boolean active) {
        Company company = requireCompany(companyId);
        company.setActive(active);
        CompanyVersion current =
                versionRepository.findByCompanyAndEffectiveToIsNull(company).orElse(null);
        return CompanyMapper.toCompany(company, current);
    }

    public void softDelete(UUID companyId) {
        Company company = requireCompany(companyId);
        companyRepository.delete(company);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID companyId, LocalDate asOf) {
        Company company = requireCompany(companyId);
        CompanyVersion version =
                asOf == null
                        ? versionRepository.findByCompanyAndEffectiveToIsNull(company).orElse(null)
                        : versionRepository.findEffectiveAt(company, asOf).orElse(null);
        return CompanyMapper.toCompany(company, version);
    }

    @Transactional(readOnly = true)
    public List<CompanyVersionResponse> listVersions(UUID companyId) {
        Company company = requireCompany(companyId);
        return versionRepository.findByCompanyOrderByEffectiveFromDesc(company).stream()
                .map(CompanyMapper::toVersion)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CompanyResponse> search(
            UUID tenantId, String code, Boolean active, String search, Pageable pageable) {
        return companyRepository
                .findAll(CompanySpecifications.matching(tenantId, code, active, search), pageable)
                .map(
                        c -> {
                            CompanyVersion current =
                                    versionRepository
                                            .findByCompanyAndEffectiveToIsNull(c)
                                            .orElse(null);
                            return CompanyMapper.toCompany(c, current);
                        });
    }

    // --- helpers ------------------------------------------------------------

    private Tenant resolveTenant(UUID tenantId) {
        if (tenantId != null) {
            return tenantRepository
                    .findById(tenantId)
                    .orElseThrow(
                            () -> new ApiException(HttpStatus.BAD_REQUEST, "Unknown tenant id"));
        }
        return tenantRepository
                .findByCode("DEFAULT")
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Default tenant is missing — V6 migration did not run"));
    }

    private Company requireCompany(UUID id) {
        return companyRepository
                .findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    private CompanyVersion openNewVersion(Company company, CompanyProfile profile) {
        EffectiveDateValidator.requireOrdered(profile.effectiveFrom(), null);
        CompanyVersion v = new CompanyVersion();
        v.setCompany(company);
        v.setEffectiveFrom(profile.effectiveFrom());
        v.setEffectiveTo(null);
        v.setName(profile.name());
        v.setLegalName(profile.legalName());
        v.setLogoUrl(profile.logoUrl());
        v.setTimezone(profile.timezone());
        v.setCurrency(profile.currency());
        v.setFiscalYearStartMonth((short) profile.fiscalYearStartMonth().intValue());
        return versionRepository.save(v);
    }

    private void clonePolicyAssignments(
            Company source, Company target, Set<PolicyType> types, LocalDate effectiveFrom) {
        if (types == null || types.isEmpty()) {
            return;
        }
        List<CompanyPolicyAssignment> sourceAssignments =
                policyAssignmentRepository.findByCompany(source);
        for (CompanyPolicyAssignment sa : sourceAssignments) {
            if (!types.contains(sa.getPolicyType())) {
                continue;
            }
            // Only carry over live assignments (still open, or open at effectiveFrom).
            if (sa.getEffectiveTo() != null && sa.getEffectiveTo().isBefore(effectiveFrom)) {
                continue;
            }
            CompanyPolicyAssignment copy = new CompanyPolicyAssignment();
            copy.setCompany(target);
            copy.setPolicyType(sa.getPolicyType());
            copy.setPolicyRef(sa.getPolicyRef());
            copy.setPolicyLabel(sa.getPolicyLabel());
            copy.setEffectiveFrom(effectiveFrom);
            copy.setEffectiveTo(null);
            policyAssignmentRepository.save(copy);
        }
    }
}
