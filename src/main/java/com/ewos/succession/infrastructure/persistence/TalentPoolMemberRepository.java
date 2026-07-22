package com.ewos.succession.infrastructure.persistence;

import com.ewos.succession.domain.TalentPoolMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TalentPoolMemberRepository extends JpaRepository<TalentPoolMember, UUID> {

    Optional<TalentPoolMember> findByIdAndTenantId(UUID id, UUID tenantId);

    List<TalentPoolMember> findAllByTenantIdAndPoolId(UUID tenantId, UUID poolId);

    List<TalentPoolMember> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);
}
