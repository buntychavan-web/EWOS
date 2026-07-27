package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreateTenantAccessGrantRequest;
import com.ewos.tenancy.api.dto.TenantAccessGrantResponse;
import com.ewos.tenancy.domain.TenantAccessGrant;
import com.ewos.tenancy.infrastructure.persistence.TenantAccessGrantRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants a user narrow, time-boxed, audited access to a tenant that isn't their own — the
 * replacement for a blanket cross-tenant bypass authority. See the Sprint 1 Tenant Model
 * Architecture Review for why: scope (one named tenant, not every tenant), duration (expires), and
 * evidence (who granted it, why, and when it was used) are the three properties a bypass authority
 * lacked.
 */
@Service
@Transactional
public class TenantAccessGrantService {

    private final TenantAccessGrantRepository repository;
    private final TenantRepository tenantRepository;
    private final TenantContext tenantContext;
    private final TenancyMapper mapper;

    public TenantAccessGrantService(
            TenantAccessGrantRepository repository,
            TenantRepository tenantRepository,
            TenantContext tenantContext,
            TenancyMapper mapper) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.tenantContext = tenantContext;
        this.mapper = mapper;
    }

    public TenantAccessGrantResponse grant(CreateTenantAccessGrantRequest request) {
        if (!tenantRepository.existsById(request.tenantId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        TenantAccessGrant grant = new TenantAccessGrant();
        grant.setUserId(request.userId());
        grant.setTenantId(request.tenantId());
        grant.setGrantedBy(tenantContext.currentUserId().orElse(null));
        grant.setReason(request.reason());
        grant.setExpiresAt(request.expiresAt());
        return mapper.toResponse(repository.save(grant));
    }

    public TenantAccessGrantResponse revoke(UUID id) {
        TenantAccessGrant grant = require(id);
        if (grant.getRevokedAt() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Grant already revoked");
        }
        grant.setRevokedAt(Instant.now());
        grant.setRevokedBy(tenantContext.currentUserId().orElse(null));
        return mapper.toResponse(grant);
    }

    @Transactional(readOnly = true)
    public List<TenantAccessGrantResponse> listForUser(UUID userId) {
        return repository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /**
     * Used by {@code TenantHeaderValidationFilter} to allow a request against a tenant that isn't
     * the caller's own, when an active grant covers it.
     */
    @Transactional(readOnly = true)
    public boolean hasActiveGrant(UUID userId, UUID tenantId) {
        return repository.existsByUserIdAndTenantIdAndRevokedAtIsNullAndExpiresAtAfter(
                userId, tenantId, Instant.now());
    }

    private TenantAccessGrant require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND, "Tenant access grant not found"));
    }
}
