package com.ewos.recruitment.infrastructure.persistence;

import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.RequisitionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobRequisitionRepository
        extends JpaRepository<JobRequisition, UUID>, JpaSpecificationExecutor<JobRequisition> {

    Optional<JobRequisition> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<JobRequisition> findByTenantIdAndCompanyIdAndRequisitionNumberIgnoreCase(
            UUID tenantId, UUID companyId, String requisitionNumber);

    boolean existsByTenantIdAndCompanyIdAndRequisitionNumberIgnoreCase(
            UUID tenantId, UUID companyId, String requisitionNumber);

    List<JobRequisition> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, RequisitionStatus status);

    Page<JobRequisition> findAllByTenantIdAndCompanyId(
            UUID tenantId, UUID companyId, Pageable page);

    long countByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, RequisitionStatus status);
}
