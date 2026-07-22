package com.ewos.exit.infrastructure.persistence;

import com.ewos.exit.domain.AlumniRecord;
import com.ewos.exit.domain.RehireEligibility;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlumniRecordRepository extends JpaRepository<AlumniRecord, UUID> {

    Optional<AlumniRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<AlumniRecord> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<AlumniRecord> findAllByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

    long countByTenantIdAndCompanyIdAndRehireEligibility(
            UUID tenantId, UUID companyId, RehireEligibility rehireEligibility);
}
