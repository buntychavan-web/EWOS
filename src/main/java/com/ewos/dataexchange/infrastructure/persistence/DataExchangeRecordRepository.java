package com.ewos.dataexchange.infrastructure.persistence;

import com.ewos.dataexchange.domain.DataExchangeRecord;
import com.ewos.dataexchange.domain.DataExchangeStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataExchangeRecordRepository extends JpaRepository<DataExchangeRecord, UUID> {

    Optional<DataExchangeRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DataExchangeRecord> findAllByTenantIdAndCompanyIdOrderByCreatedAtDesc(
            UUID tenantId, UUID companyId);

    List<DataExchangeRecord> findAllByTenantIdAndCompanyIdAndStatusOrderByCreatedAtDesc(
            UUID tenantId, UUID companyId, DataExchangeStatus status);

    List<DataExchangeRecord> findAllByTenantIdAndCorrelationIdOrderByCreatedAtDesc(
            UUID tenantId, String correlationId);
}
