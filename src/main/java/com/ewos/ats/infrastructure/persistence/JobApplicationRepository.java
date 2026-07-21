package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.ApplicationStatus;
import com.ewos.ats.domain.JobApplication;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, UUID>, JpaSpecificationExecutor<JobApplication> {

    Optional<JobApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndApplicationNumberIgnoreCase(
            UUID tenantId, UUID companyId, String applicationNumber);

    boolean existsByTenantIdAndCompanyIdAndCandidateIdAndJobRequisitionId(
            UUID tenantId, UUID companyId, UUID candidateId, UUID jobRequisitionId);

    List<JobApplication> findAllByTenantIdAndCandidateIdOrderByAppliedAtDesc(
            UUID tenantId, UUID candidateId);

    List<JobApplication> findAllByTenantIdAndJobRequisitionIdOrderByAppliedAtDesc(
            UUID tenantId, UUID jobRequisitionId);

    Page<JobApplication> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, ApplicationStatus status, Pageable page);
}
