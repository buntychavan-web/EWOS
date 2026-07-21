package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.BankAdvice;
import com.ewos.payroll.domain.BankAdviceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAdviceRepository extends JpaRepository<BankAdvice, UUID> {

    Optional<BankAdvice> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndAdviceNumberIgnoreCase(
            UUID tenantId, UUID companyId, String adviceNumber);

    @Query(
            "select a from BankAdvice a where a.tenantId = :tenantId and a.payrollRun.id ="
                    + " :runId order by a.adviceDate desc")
    List<BankAdvice> findAllForRun(@Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    List<BankAdvice> findAllByTenantIdAndCompanyIdAndStatusOrderByAdviceDateDesc(
            UUID tenantId, UUID companyId, BankAdviceStatus status);
}
