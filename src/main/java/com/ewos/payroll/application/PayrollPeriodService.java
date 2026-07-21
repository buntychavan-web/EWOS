package com.ewos.payroll.application;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreatePayrollPeriodRequest;
import com.ewos.payroll.api.dto.PayrollPeriodResponse;
import com.ewos.payroll.api.dto.UpdatePayrollPeriodRequest;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollPolicy;
import com.ewos.payroll.domain.events.PayrollEvent;
import com.ewos.payroll.domain.events.PayrollEventType;
import com.ewos.payroll.infrastructure.persistence.PayrollPeriodRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PayrollPeriodService {

    private final PayrollPeriodRepository repository;
    private final PayrollPolicy policy;
    private final PayrollMapper mapper;
    private final ApplicationEventPublisher events;

    public PayrollPeriodService(
            PayrollPeriodRepository repository,
            PayrollPolicy policy,
            PayrollMapper mapper,
            ApplicationEventPublisher events) {
        this.repository = repository;
        this.policy = policy;
        this.mapper = mapper;
        this.events = events;
    }

    public PayrollPeriodResponse create(CreatePayrollPeriodRequest request) {
        if (repository.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                request.tenantId(), request.companyId(), request.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Payroll period code already in use for this company");
        }
        if (request.periodEnd().isBefore(request.periodStart())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "periodEnd must not be before periodStart");
        }
        PayrollPeriod p = new PayrollPeriod();
        p.setTenantId(request.tenantId());
        p.setCompanyId(request.companyId());
        p.setCode(request.code());
        p.setName(request.name());
        p.setFrequency(request.frequency());
        p.setPeriodStart(request.periodStart());
        p.setPeriodEnd(request.periodEnd());
        p.setPayDate(request.payDate());
        p.setStatus(PayrollPeriodStatus.OPEN);
        PayrollPeriod saved = repository.save(p);
        publish(PayrollEventType.PERIOD_OPENED, saved);
        return mapper.toResponse(saved);
    }

    public PayrollPeriodResponse update(
            UUID tenantId, UUID id, UpdatePayrollPeriodRequest request) {
        PayrollPeriod p = require(tenantId, id);
        policy.assertEditable(p);
        if (request.code() != null && !request.code().equalsIgnoreCase(p.getCode())) {
            if (repository.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                    tenantId, p.getCompanyId(), request.code())) {
                throw new ApiException(
                        HttpStatus.CONFLICT, "Payroll period code already in use for this company");
            }
            p.setCode(request.code());
        }
        if (request.name() != null) {
            p.setName(request.name());
        }
        if (request.frequency() != null) {
            p.setFrequency(request.frequency());
        }
        if (request.periodStart() != null) {
            p.setPeriodStart(request.periodStart());
        }
        if (request.periodEnd() != null) {
            p.setPeriodEnd(request.periodEnd());
        }
        if (p.getPeriodEnd().isBefore(p.getPeriodStart())) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "periodEnd must not be before periodStart");
        }
        if (request.payDate() != null) {
            p.setPayDate(request.payDate());
        }
        return mapper.toResponse(p);
    }

    public PayrollPeriodResponse lock(UUID tenantId, UUID id) {
        PayrollPeriod p = require(tenantId, id);
        policy.assertLockable(p);
        UUID actor = requireActor();
        p.setStatus(PayrollPeriodStatus.LOCKED);
        p.setLockedAt(Instant.now());
        p.setLockedBy(actor);
        publish(PayrollEventType.PERIOD_LOCKED, p);
        return mapper.toResponse(p);
    }

    public PayrollPeriodResponse close(UUID tenantId, UUID id) {
        PayrollPeriod p = require(tenantId, id);
        policy.assertClosable(p);
        UUID actor = requireActor();
        p.setStatus(PayrollPeriodStatus.CLOSED);
        p.setClosedAt(Instant.now());
        p.setClosedBy(actor);
        publish(PayrollEventType.PERIOD_CLOSED, p);
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public PayrollPeriodResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PayrollPeriodResponse> forCompany(UUID tenantId, UUID companyId) {
        return repository.findAllForCompany(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PayrollPeriodResponse> byStatus(UUID tenantId, PayrollPeriodStatus status) {
        return repository
                .findAllByTenantIdAndStatusOrderByPeriodStartDesc(tenantId, status)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    public PayrollPeriod require(UUID tenantId, UUID id) {
        return repository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Payroll period not found"));
    }

    private void publish(PayrollEventType type, PayrollPeriod p) {
        events.publishEvent(
                new PayrollEvent(
                        type,
                        p.getTenantId(),
                        p.getCompanyId(),
                        null,
                        p.getId(),
                        null,
                        null,
                        null,
                        null,
                        currentActor(),
                        Instant.now()));
    }

    private static UUID requireActor() {
        UUID actor = currentActor();
        if (actor == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED, "Authenticated user required for this action");
        }
        return actor;
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
