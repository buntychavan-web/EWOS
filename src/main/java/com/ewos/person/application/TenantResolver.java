package com.ewos.person.application;

import com.ewos.common.exception.ApiException;
import com.ewos.company.domain.Tenant;
import com.ewos.company.infrastructure.persistence.TenantRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class TenantResolver {

    private final TenantRepository tenantRepository;

    TenantResolver(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    Tenant resolve(UUID tenantId) {
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
}
