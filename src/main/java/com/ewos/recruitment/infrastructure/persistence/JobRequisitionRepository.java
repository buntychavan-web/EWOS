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

    /**
     * Sprint 27B — server-side scoped and paginated: powers the hiring manager's read-only "pending
     * requisitions" card in the unified approvals inbox. Unlike Leave/Timesheet's "employee's
     * manager" relationship, {@code hiringManager} is a direct field on the requisition itself —
     * there is no subject employee to walk a manager chain from.
     */
    Page<JobRequisition> findAllByTenantIdAndStatusAndHiringManagerId(
            UUID tenantId, RequisitionStatus status, UUID hiringManagerId, Pageable pageable);
}
