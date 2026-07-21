package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.FinalSettlement;
import com.ewos.payroll.domain.FinalSettlementStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinalSettlementRepository extends JpaRepository<FinalSettlement, UUID> {

    Optional<FinalSettlement> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select s from FinalSettlement s where s.tenantId = :tenantId "
                    + "and s.employee.id = :employeeId and s.status <> "
                    + "com.ewos.payroll.domain.FinalSettlementStatus.CANCELLED")
    Optional<FinalSettlement> findLiveForEmployee(
            @Param("tenantId") UUID tenantId, @Param("employeeId") UUID employeeId);

    List<FinalSettlement> findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID companyId, FinalSettlementStatus status);
}
