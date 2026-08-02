package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreatePfConfigurationRequest;
import com.ewos.payroll.api.dto.PfConfigurationResponse;
import com.ewos.payroll.api.dto.UpdatePfConfigurationRequest;
import com.ewos.payroll.domain.PfConfiguration;
import com.ewos.payroll.infrastructure.persistence.PfConfigurationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin CRUD for Provident Fund configuration. {@code companyId == null} is the tenant-wide
 * default.
 */
@Service
@Transactional
public class PfConfigurationService {

    private final PfConfigurationRepository repository;
    private final PayrollMapper mapper;
    private final ClientAccessGuard guard;

    public PfConfigurationService(
            PfConfigurationRepository repository, PayrollMapper mapper, ClientAccessGuard guard) {
        this.repository = repository;
        this.mapper = mapper;
        this.guard = guard;
    }

    public PfConfigurationResponse create(CreatePfConfigurationRequest request) {
        if (request.companyId() != null) {
            guard.requireAccessForCompany(request.companyId());
        }
        PfConfiguration c = new PfConfiguration();
        c.setTenantId(request.tenantId());
        c.setCompanyId(request.companyId());
        c.setWageCeiling(request.wageCeiling());
        c.setEpsWageCeiling(request.epsWageCeiling());
        c.setEmployeeRatePct(request.employeeRatePct());
        c.setEmployerPfRatePct(request.employerPfRatePct());
        c.setEpsRatePct(request.epsRatePct());
        c.setEffectiveFrom(request.effectiveFrom());
        c.setEffectiveTo(request.effectiveTo());
        if (request.active() != null) {
            c.setActive(request.active());
        }
        return mapper.toResponse(repository.save(c));
    }

    public PfConfigurationResponse update(UUID tenantId, UUID id, UpdatePfConfigurationRequest r) {
        PfConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        if (r.wageCeiling() != null) {
            c.setWageCeiling(r.wageCeiling());
        }
        if (r.epsWageCeiling() != null) {
            c.setEpsWageCeiling(r.epsWageCeiling());
        }
        if (r.employeeRatePct() != null) {
            c.setEmployeeRatePct(r.employeeRatePct());
        }
        if (r.employerPfRatePct() != null) {
            c.setEmployerPfRatePct(r.employerPfRatePct());
        }
        if (r.epsRatePct() != null) {
            c.setEpsRatePct(r.epsRatePct());
        }
        if (r.effectiveFrom() != null) {
            c.setEffectiveFrom(r.effectiveFrom());
        }
        if (r.effectiveTo() != null) {
            c.setEffectiveTo(r.effectiveTo());
        }
        if (r.active() != null) {
            c.setActive(r.active());
        }
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public PfConfigurationResponse getById(UUID tenantId, UUID id) {
        PfConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        return mapper.toResponse(c);
    }

    /** Tenant-wide default plus any company-specific rows, most specific/recent first. */
    @Transactional(readOnly = true)
    public List<PfConfigurationResponse> forCompany(UUID tenantId, UUID companyId) {
        guard.requireAccessForCompany(companyId);
        return repository.findCandidates(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public void delete(UUID tenantId, UUID id) {
        PfConfiguration c = require(tenantId, id);
        if (c.getCompanyId() != null) {
            guard.requireAccessForCompany(c.getCompanyId());
        }
        repository.delete(c);
    }

    private PfConfiguration require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "PF configuration not found"));
    }
}
