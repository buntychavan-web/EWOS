package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollRunReopenAuthorization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRunReopenAuthorizationRepository
        extends JpaRepository<PayrollRunReopenAuthorization, UUID> {

    @Query(
            "select a from PayrollRunReopenAuthorization a where a.tenantId = :tenantId and"
                    + " a.payrollRun.id = :runId and a.status ="
                    + " com.ewos.payroll.domain.PayrollRunReopenAuthorizationStatus.ACTIVE")
    Optional<PayrollRunReopenAuthorization> findActiveForRun(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    @Query(
            "select a from PayrollRunReopenAuthorization a where a.tenantId = :tenantId and"
                    + " a.payrollRun.id = :runId order by a.authorizedAt asc")
    List<PayrollRunReopenAuthorization> findAllForRun(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);
}
