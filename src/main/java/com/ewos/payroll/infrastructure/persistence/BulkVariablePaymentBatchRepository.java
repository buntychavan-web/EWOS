package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.BulkVariablePaymentBatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BulkVariablePaymentBatchRepository
        extends JpaRepository<BulkVariablePaymentBatch, UUID> {

    Optional<BulkVariablePaymentBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    List<BulkVariablePaymentBatch> findAllByTenantIdAndCompanyIdOrderByCreatedAtDesc(
            UUID tenantId, UUID companyId);
}
