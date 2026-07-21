package com.ewos.ats.infrastructure.persistence;

import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CandidateRepository
        extends JpaRepository<Candidate, UUID>, JpaSpecificationExecutor<Candidate> {

    Optional<Candidate> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCandidateNumberIgnoreCase(
            UUID tenantId, UUID companyId, String candidateNumber);

    Optional<Candidate> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

    List<Candidate> findAllByTenantIdAndPhoneDigits(UUID tenantId, String phoneDigits);

    Page<Candidate> findAllByTenantIdAndCompanyId(UUID tenantId, UUID companyId, Pageable page);

    Page<Candidate> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, CandidateStatus status, Pageable page);

    long countByTenantIdAndCompanyId(UUID tenantId, UUID companyId);
}
