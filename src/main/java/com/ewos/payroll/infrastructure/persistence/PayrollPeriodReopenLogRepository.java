package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollPeriodReopenLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollPeriodReopenLogRepository
        extends JpaRepository<PayrollPeriodReopenLog, UUID> {

    @Query(
            "select l from PayrollPeriodReopenLog l where l.tenantId = :tenantId and"
                    + " l.payrollPeriod.id = :periodId order by l.reopenedAt asc")
    List<PayrollPeriodReopenLog> findAllForPeriod(
            @Param("tenantId") UUID tenantId, @Param("periodId") UUID periodId);
}
