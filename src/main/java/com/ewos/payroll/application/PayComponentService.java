package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreatePayComponentRequest;
import com.ewos.payroll.api.dto.PayComponentResponse;
import com.ewos.payroll.api.dto.UpdatePayComponentRequest;
import com.ewos.payroll.domain.PayComponent;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.PayComponentRepository;
import com.ewos.shared.exception.ApiException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metadata catalogue for pay components. Redis-cached (short TTL) — components are read on every
 * payroll run but rarely edited.
 */
@Service
@Transactional
public class PayComponentService {

    private static final String CACHE = "payroll.component";

    private final PayComponentRepository repository;
    private final PayrollMapper mapper;
    private final ApplicationEventPublisher events;

    public PayComponentService(
            PayComponentRepository repository,
            PayrollMapper mapper,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.mapper = mapper;
        this.events = events;
    }

    @CacheEvict(value = CACHE, allEntries = true)
    public PayComponentResponse create(CreatePayComponentRequest request) {
        if (repository.existsByTenantIdAndCodeIgnoreCase(request.tenantId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Pay component code already in use for this tenant");
        }
        PayComponent c = new PayComponent();
        c.setTenantId(request.tenantId());
        c.setCode(request.code());
        c.setName(request.name());
        c.setDescription(request.description());
        c.setKind(request.kind());
        c.setCalculationType(request.calculationType());
        if (request.defaultAmount() != null) {
            c.setDefaultAmount(request.defaultAmount());
        }
        if (request.defaultPercentage() != null) {
            c.setDefaultPercentage(request.defaultPercentage());
        }
        if (request.taxable() != null) {
            c.setTaxable(request.taxable());
        }
        if (request.active() != null) {
            c.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            c.setSortOrder(request.sortOrder());
        }
        PayComponent saved = repository.save(c);
        publish(PayrollEventType.COMPONENT_CHANGED, saved.getTenantId(), saved.getId(), null);
        return mapper.toResponse(saved);
    }

    @CacheEvict(value = CACHE, allEntries = true)
    public PayComponentResponse update(UUID tenantId, UUID id, UpdatePayComponentRequest request) {
        PayComponent c = require(tenantId, id);
        if (request.code() != null && !request.code().equalsIgnoreCase(c.getCode())) {
            if (repository.existsByTenantIdAndCodeIgnoreCase(tenantId, request.code())) {
                throw new ApiException(
                        HttpStatus.CONFLICT, "Pay component code already in use for this tenant");
            }
            c.setCode(request.code());
        }
        if (request.name() != null) {
            c.setName(request.name());
        }
        if (request.description() != null) {
            c.setDescription(request.description());
        }
        if (request.kind() != null) {
            c.setKind(request.kind());
        }
        if (request.calculationType() != null) {
            c.setCalculationType(request.calculationType());
        }
        if (request.defaultAmount() != null) {
            c.setDefaultAmount(request.defaultAmount());
        }
        if (request.defaultPercentage() != null) {
            c.setDefaultPercentage(request.defaultPercentage());
        }
        if (request.taxable() != null) {
            c.setTaxable(request.taxable());
        }
        if (request.active() != null) {
            c.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            c.setSortOrder(request.sortOrder());
        }
        publish(PayrollEventType.COMPONENT_CHANGED, c.getTenantId(), c.getId(), null);
        return mapper.toResponse(c);
    }

    @Cacheable(value = CACHE, key = "#tenantId + ':' + #id")
    @Transactional(readOnly = true)
    public PayComponentResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PayComponentResponse> list(UUID tenantId) {
        return repository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @CacheEvict(value = CACHE, allEntries = true)
    public void delete(UUID tenantId, UUID id) {
        PayComponent c = require(tenantId, id);
        repository.delete(c);
        publish(PayrollEventType.COMPONENT_CHANGED, c.getTenantId(), c.getId(), null);
    }

    public PayComponent require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Pay component not found"));
    }

    private void publish(
            PayrollEventType type, UUID tenantId, UUID componentId, BigDecimal amount) {
        events.publishEvent(
                new PayrollEvent(
                        type,
                        tenantId,
                        null,
                        componentId,
                        null,
                        null,
                        null,
                        null,
                        amount,
                        currentActor(),
                        Instant.now()));
    }

    private static UUID currentActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) {
                return null;
            }
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
