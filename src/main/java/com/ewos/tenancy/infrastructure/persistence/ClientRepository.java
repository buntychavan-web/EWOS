package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.Client;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Client> findAllByTenantIdOrderByLegalNameAsc(UUID tenantId);

    List<Client> findAllByIdInOrderByLegalNameAsc(List<UUID> ids);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
