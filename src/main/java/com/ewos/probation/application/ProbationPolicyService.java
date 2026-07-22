package com.ewos.probation.application;

import com.ewos.probation.api.ProbationMapper;
import com.ewos.probation.api.dto.CreateProbationPolicyRequest;
import com.ewos.probation.api.dto.ProbationPolicyResponse;
import com.ewos.probation.api.dto.UpdateProbationPolicyRequest;
import com.ewos.probation.domain.ProbationPolicy;
import com.ewos.probation.domain.events.ProbationEvent;
import com.ewos.probation.domain.events.ProbationEventType;
import com.ewos.probation.infrastructure.persistence.ProbationPolicyRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProbationPolicyService {

    private final ProbationPolicyRepository policies;
    private final ProbationMapper mapper;
    private final ApplicationEventPublisher events;

    public ProbationPolicyService(
            ProbationPolicyRepository policies,
            ProbationMapper mapper,
            ApplicationEventPublisher events) {
        this.policies = policies;
        this.mapper = mapper;
        this.events = events;
    }

    public ProbationPolicyResponse create(CreateProbationPolicyRequest req) {
        if (policies.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Probation policy code already exists: " + req.code());
        }
        ProbationPolicy p = new ProbationPolicy();
        p.setTenantId(req.tenantId());
        p.setCompanyId(req.companyId());
        p.setCode(req.code());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setDefaultPeriodDays(req.defaultPeriodDays());
        p.setMaxExtensionDays(req.maxExtensionDays());
        p.setAllowEarlyConfirm(req.allowEarlyConfirm());
        p.setActive(true);
        p = policies.save(p);
        publish(ProbationEventType.POLICY_CREATED, p, null);
        return mapper.toResponse(p);
    }

    public ProbationPolicyResponse update(
            UUID tenantId, UUID id, UpdateProbationPolicyRequest req) {
        ProbationPolicy p = require(tenantId, id);
        boolean deactivating = p.isActive() && !req.active();
        p.setName(req.name());
        p.setDescription(req.description());
        p.setDefaultPeriodDays(req.defaultPeriodDays());
        p.setMaxExtensionDays(req.maxExtensionDays());
        p.setAllowEarlyConfirm(req.allowEarlyConfirm());
        p.setActive(req.active());
        publish(
                deactivating
                        ? ProbationEventType.POLICY_DEACTIVATED
                        : ProbationEventType.POLICY_UPDATED,
                p,
                null);
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public ProbationPolicyResponse getById(UUID tenantId, UUID id) {
        return mapper.toResponse(require(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<ProbationPolicyResponse> listActive(UUID tenantId, UUID companyId) {
        return policies.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Package-private accessor used by ProbationService. */
    ProbationPolicy require(UUID tenantId, UUID id) {
        return policies.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Probation policy not found"));
    }

    private void publish(ProbationEventType type, ProbationPolicy p, String detail) {
        events.publishEvent(
                new ProbationEvent(
                        type,
                        p.getTenantId(),
                        p.getCompanyId(),
                        null,
                        null,
                        p.getId(),
                        null,
                        detail,
                        ProbationSecurity.currentActor(),
                        Instant.now()));
    }
}
