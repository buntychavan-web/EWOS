package com.ewos.reimbursement.infrastructure.persistence;

import com.ewos.reimbursement.domain.ReimbursementClaimAttachment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReimbursementClaimAttachmentRepository
        extends JpaRepository<ReimbursementClaimAttachment, UUID> {

    Optional<ReimbursementClaimAttachment> findByIdAndTenantId(UUID id, UUID tenantId);

    List<ReimbursementClaimAttachment> findAllByTenantIdAndClaimIdOrderByUploadedAtDesc(
            UUID tenantId, UUID claimId);
}
