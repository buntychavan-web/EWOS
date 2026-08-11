package com.ewos.reimbursement.infrastructure.persistence;

import com.ewos.reimbursement.domain.ReimbursementClaimHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReimbursementClaimHistoryRepository
        extends JpaRepository<ReimbursementClaimHistory, UUID> {

    List<ReimbursementClaimHistory> findAllByClaimIdOrderByOccurredAtAsc(UUID claimId);
}
