package com.ewos.probation.infrastructure.persistence;

import com.ewos.probation.domain.ProbationRecord;
import com.ewos.probation.domain.ProbationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProbationRecordRepository extends JpaRepository<ProbationRecord, UUID> {

    Optional<ProbationRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<ProbationRecord> findByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<ProbationRecord> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, ProbationStatus status);

    long countByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, ProbationStatus status);

    @Query(
            "select r from ProbationRecord r "
                    + "where r.tenantId = :tenantId "
                    + "  and r.companyId = :companyId "
                    + "  and r.status in :statuses "
                    + "  and coalesce(r.extendedEnd, r.periodEnd) <= :through "
                    + "order by coalesce(r.extendedEnd, r.periodEnd) asc")
    List<ProbationRecord> findDueBy(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId,
            @Param("statuses") List<ProbationStatus> statuses,
            @Param("through") LocalDate through);
}
