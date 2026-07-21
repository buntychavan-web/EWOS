package com.ewos.payroll.application;

import com.ewos.payroll.api.dto.BusinessUnitResponse;
import com.ewos.payroll.api.dto.CostCentreResponse;
import com.ewos.payroll.api.dto.CreateBusinessUnitRequest;
import com.ewos.payroll.api.dto.CreateCostCentreRequest;
import com.ewos.payroll.api.dto.CreateGLAccountRequest;
import com.ewos.payroll.api.dto.CreateGLMappingRequest;
import com.ewos.payroll.api.dto.GLAccountResponse;
import com.ewos.payroll.api.dto.GLMappingResponse;
import com.ewos.payroll.domain.AllocationDimension;
import com.ewos.payroll.domain.BusinessUnit;
import com.ewos.payroll.domain.CostCentre;
import com.ewos.payroll.domain.GLAccount;
import com.ewos.payroll.domain.GLMapping;
import com.ewos.payroll.infrastructure.persistence.BusinessUnitRepository;
import com.ewos.payroll.infrastructure.persistence.CostCentreRepository;
import com.ewos.payroll.infrastructure.persistence.GLAccountRepository;
import com.ewos.payroll.infrastructure.persistence.GLMappingRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD for cost centres, business units, GL accounts, and GL mappings. */
@Service
@Transactional
public class GlConfigService {

    private final CostCentreRepository costCentres;
    private final BusinessUnitRepository businessUnits;
    private final GLAccountRepository accounts;
    private final GLMappingRepository mappings;

    public GlConfigService(
            CostCentreRepository costCentres,
            BusinessUnitRepository businessUnits,
            GLAccountRepository accounts,
            GLMappingRepository mappings) {
        this.costCentres = costCentres;
        this.businessUnits = businessUnits;
        this.accounts = accounts;
        this.mappings = mappings;
    }

    // ---------- cost centres ------------------------------------------------

    public CostCentreResponse createCostCentre(CreateCostCentreRequest r) {
        if (costCentres.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                r.tenantId(), r.companyId(), r.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Cost centre code already in use");
        }
        CostCentre c = new CostCentre();
        c.setTenantId(r.tenantId());
        c.setCompanyId(r.companyId());
        c.setCode(r.code());
        c.setName(r.name());
        c.setDescription(r.description());
        if (r.active() != null) {
            c.setActive(r.active());
        }
        return toResponse(costCentres.save(c));
    }

    @Transactional(readOnly = true)
    public List<CostCentreResponse> listCostCentres(UUID tenantId, UUID companyId) {
        return costCentres.findAllByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId).stream()
                .map(GlConfigService::toResponse)
                .toList();
    }

    public void deleteCostCentre(UUID tenantId, UUID id) {
        CostCentre c =
                costCentres
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Cost centre not found"));
        costCentres.delete(c);
    }

    // ---------- business units ---------------------------------------------

    public BusinessUnitResponse createBusinessUnit(CreateBusinessUnitRequest r) {
        if (businessUnits.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                r.tenantId(), r.companyId(), r.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "Business unit code already in use");
        }
        BusinessUnit b = new BusinessUnit();
        b.setTenantId(r.tenantId());
        b.setCompanyId(r.companyId());
        b.setCode(r.code());
        b.setName(r.name());
        b.setDescription(r.description());
        if (r.active() != null) {
            b.setActive(r.active());
        }
        return toResponse(businessUnits.save(b));
    }

    @Transactional(readOnly = true)
    public List<BusinessUnitResponse> listBusinessUnits(UUID tenantId, UUID companyId) {
        return businessUnits
                .findAllByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId)
                .stream()
                .map(GlConfigService::toResponse)
                .toList();
    }

    public void deleteBusinessUnit(UUID tenantId, UUID id) {
        BusinessUnit b =
                businessUnits
                        .findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Business unit not found"));
        businessUnits.delete(b);
    }

    // ---------- GL accounts -------------------------------------------------

    public GLAccountResponse createAccount(CreateGLAccountRequest r) {
        if (accounts.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                r.tenantId(), r.companyId(), r.code())) {
            throw new ApiException(HttpStatus.CONFLICT, "GL account code already in use");
        }
        GLAccount a = new GLAccount();
        a.setTenantId(r.tenantId());
        a.setCompanyId(r.companyId());
        a.setCode(r.code());
        a.setName(r.name());
        a.setAccountType(r.accountType());
        if (r.active() != null) {
            a.setActive(r.active());
        }
        return toResponse(accounts.save(a));
    }

    @Transactional(readOnly = true)
    public List<GLAccountResponse> listAccounts(UUID tenantId, UUID companyId) {
        return accounts.findAllByTenantIdAndCompanyIdOrderByCodeAsc(tenantId, companyId).stream()
                .map(GlConfigService::toResponse)
                .toList();
    }

    public void deleteAccount(UUID tenantId, UUID id) {
        GLAccount a =
                accounts.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "GL account not found"));
        accounts.delete(a);
    }

    // ---------- GL mappings ------------------------------------------------

    public GLMappingResponse createMapping(CreateGLMappingRequest r) {
        if (mappings.findActive(r.tenantId(), r.companyId(), r.sourceKind(), r.sourceCode())
                .isPresent()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Mapping already exists for " + r.sourceKind() + "/" + r.sourceCode());
        }
        GLAccount debit =
                accounts.findByIdAndTenantId(r.debitAccountId(), r.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Debit account not found"));
        GLAccount credit =
                accounts.findByIdAndTenantId(r.creditAccountId(), r.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST,
                                                "Credit account not found"));
        GLMapping m = new GLMapping();
        m.setTenantId(r.tenantId());
        m.setCompanyId(r.companyId());
        m.setSourceKind(r.sourceKind());
        m.setSourceCode(r.sourceCode());
        m.setDebitAccount(debit);
        m.setCreditAccount(credit);
        m.setAllocationDimension(
                r.allocationDimension() != null
                        ? r.allocationDimension()
                        : AllocationDimension.NONE);
        m.setDescription(r.description());
        if (r.active() != null) {
            m.setActive(r.active());
        }
        return toResponse(mappings.save(m));
    }

    @Transactional(readOnly = true)
    public List<GLMappingResponse> listMappings(UUID tenantId, UUID companyId) {
        return mappings
                .findAllByTenantIdAndCompanyIdOrderBySourceKindAscSourceCodeAsc(tenantId, companyId)
                .stream()
                .map(GlConfigService::toResponse)
                .toList();
    }

    public void deleteMapping(UUID tenantId, UUID id) {
        GLMapping m =
                mappings.findByIdAndTenantId(id, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "GL mapping not found"));
        mappings.delete(m);
    }

    // ---------- mappers -----------------------------------------------------

    static CostCentreResponse toResponse(CostCentre c) {
        return new CostCentreResponse(
                c.getId(),
                c.getTenantId(),
                c.getCompanyId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.isActive(),
                c.getVersionNo());
    }

    static BusinessUnitResponse toResponse(BusinessUnit b) {
        return new BusinessUnitResponse(
                b.getId(),
                b.getTenantId(),
                b.getCompanyId(),
                b.getCode(),
                b.getName(),
                b.getDescription(),
                b.isActive(),
                b.getVersionNo());
    }

    static GLAccountResponse toResponse(GLAccount a) {
        return new GLAccountResponse(
                a.getId(),
                a.getTenantId(),
                a.getCompanyId(),
                a.getCode(),
                a.getName(),
                a.getAccountType(),
                a.isActive(),
                a.getVersionNo());
    }

    static GLMappingResponse toResponse(GLMapping m) {
        return new GLMappingResponse(
                m.getId(),
                m.getTenantId(),
                m.getCompanyId(),
                m.getSourceKind(),
                m.getSourceCode(),
                m.getDebitAccount() != null ? m.getDebitAccount().getId() : null,
                m.getDebitAccount() != null ? m.getDebitAccount().getCode() : null,
                m.getCreditAccount() != null ? m.getCreditAccount().getId() : null,
                m.getCreditAccount() != null ? m.getCreditAccount().getCode() : null,
                m.getAllocationDimension(),
                m.getDescription(),
                m.isActive(),
                m.getVersionNo());
    }
}
