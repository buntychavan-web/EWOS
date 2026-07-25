package com.ewos.integration.infrastructure.persistence;

import com.ewos.integration.domain.IntegrationExecutionOutcome;
import com.ewos.integration.domain.IntegrationExecutionRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationExecutionRecordRepository
        extends JpaRepository<IntegrationExecutionRecord, UUID> {

    List<IntegrationExecutionRecord> findAllByDataExchangeRecordIdOrderByStartedAtDesc(
            UUID dataExchangeRecordId);

    int countByDataExchangeRecordId(UUID dataExchangeRecordId);

    List<IntegrationExecutionRecord> findAllByTenantIdAndCompanyIdOrderByStartedAtDesc(
            UUID tenantId, UUID companyId);

    List<IntegrationExecutionRecord> findAllByTenantIdAndCompanyIdAndOutcomeOrderByStartedAtDesc(
            UUID tenantId, UUID companyId, IntegrationExecutionOutcome outcome);
}
