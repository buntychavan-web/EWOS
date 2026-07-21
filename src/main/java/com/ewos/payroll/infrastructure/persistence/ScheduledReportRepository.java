package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.ScheduledReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledReportRepository extends JpaRepository<ScheduledReport, UUID> {
    Optional<ScheduledReport> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ScheduledReport> findAllByTenantIdAndCompanyIdOrderByNameAsc(
            UUID tenantId, UUID companyId);

    List<ScheduledReport> findAllByTenantIdAndCompanyIdAndActiveOrderByNameAsc(
            UUID tenantId, UUID companyId, boolean active);
}
