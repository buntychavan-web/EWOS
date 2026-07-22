package com.ewos.learning.infrastructure.persistence;

import com.ewos.learning.domain.Certification;
import com.ewos.learning.domain.CertificationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {

    Optional<Certification> findByIdAndTenantId(UUID id, UUID tenantId);

    List<Certification> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<Certification> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, CertificationStatus status);

    @Query(
            "select c from Certification c "
                    + "where c.tenantId = :tenantId "
                    + "  and c.companyId = :companyId "
                    + "  and c.status = 'ACTIVE' "
                    + "  and c.expiresAt is not null "
                    + "  and c.expiresAt <= :through "
                    + "order by c.expiresAt asc")
    List<Certification> findExpiringBy(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("through") LocalDate through);
}
