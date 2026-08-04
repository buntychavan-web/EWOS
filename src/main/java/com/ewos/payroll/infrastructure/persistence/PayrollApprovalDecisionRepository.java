package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollApprovalDecision;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollApprovalDecisionRepository
        extends JpaRepository<PayrollApprovalDecision, UUID> {

    @Query(
            "select d from PayrollApprovalDecision d where d.tenantId = :tenantId and"
                    + " d.approvalRequest.id = :requestId order by d.decidedAt asc")
    List<PayrollApprovalDecision> findAllForRequest(
            @Param("tenantId") UUID tenantId, @Param("requestId") UUID requestId);
}
