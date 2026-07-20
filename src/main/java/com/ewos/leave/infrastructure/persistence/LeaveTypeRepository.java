package com.ewos.leave.infrastructure.persistence;

import com.ewos.leave.domain.LeaveType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByIdAndTenantId(UUID id, UUID tenantId);

    List<LeaveType> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);
}
