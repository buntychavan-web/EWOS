package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollJournal;
import com.ewos.payroll.domain.PayrollJournalStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollJournalRepository extends JpaRepository<PayrollJournal, UUID> {

    Optional<PayrollJournal> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndJournalNumberIgnoreCase(
            UUID tenantId, UUID companyId, String journalNumber);

    @Query(
            "select j from PayrollJournal j where j.tenantId = :tenantId "
                    + "and j.payrollRun.id = :runId order by j.journalDate desc")
    List<PayrollJournal> findAllForRun(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    List<PayrollJournal> findAllByTenantIdAndCompanyIdAndStatusOrderByJournalDateDesc(
            UUID tenantId, UUID companyId, PayrollJournalStatus status);
}
