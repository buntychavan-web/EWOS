package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreatePayGroupRequest;
import com.ewos.payroll.api.dto.PayGroupResponse;
import com.ewos.payroll.api.dto.UpdatePayGroupRequest;
import com.ewos.payroll.domain.PayGroup;
import com.ewos.payroll.infrastructure.persistence.PayGroupRepository;
import com.ewos.shared.exception.ApiException;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Pay-group management: per-company cohorts that share a payroll cadence and currency. */
@Service
@Transactional
public class PayGroupService {

    private static final String CACHE = "payroll.paygroup";

    private final PayGroupRepository repository;
    private final PayrollMapper mapper;

    public PayGroupService(PayGroupRepository repository, PayrollMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @CacheEvict(value = CACHE, allEntries = true)
    public PayGroupResponse create(CreatePayGroupRequest request) {
        if (repository.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                request.tenantId(), request.companyId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Pay-group code already in use for this company");
        }
        PayGroup g = new PayGroup();
        g.setTenantId(request.tenantId());
        g.setCompanyId(request.companyId());
        g.setCode(request.code());
        g.setName(request.name());
        g.setDescription(request.description());
        g.setFrequency(request.frequency());
        g.setCurrency(request.currency());
        g.setPayDayOfMonth(request.payDayOfMonth());
        if (request.active() != null) {
            g.setActive(request.active());
        }
        return mapper.toResponse(repository.save(g));
    }

    @CacheEvict(value = CACHE, allEntries = true)
    public PayGroupResponse update(UUID tenantId, UUID id, UpdatePayGroupRequest request) {
        PayGroup g = require(tenantId, id);
        if (request.name() != null) {
            g.setName(request.name());
        }
        if (request.description() != null) {
            g.setDescription(request.description());
        }
        if (request.frequency() != null) {
            g.setFrequency(request.frequency());
        }
        if (request.currency() != null) {
            g.setCurrency(request.currency());
        }
        if (request.payDayOfMonth() != null) {
            g.setPayDayOfMonth(request.payDayOfMonth());
        }
        if (request.active() != null) {
            g.setActive(request.active());
        }
        return mapper.toResponse(g);
    }

    @Cacheable(value = CACHE, key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public PayGroupResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PayGroupResponse> forCompany(UUID tenantId, UUID companyId) {
        return repository.findAllByTenantIdAndCompanyIdOrderByNameAsc(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @CacheEvict(value = CACHE, allEntries = true)
    public void delete(UUID tenantId, UUID id) {
        repository.delete(require(tenantId, id));
    }

    public PayGroup require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Pay group not found"));
    }
}
