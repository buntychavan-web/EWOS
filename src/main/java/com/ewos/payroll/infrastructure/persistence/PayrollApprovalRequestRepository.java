package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollApprovalRequest;
import com.ewos.payroll.domain.PayrollApprovalRequestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollApprovalRequestRepository
        extends JpaRepository<PayrollApprovalRequest, UUID> {

    @Query(
            "select r from PayrollApprovalRequest r where r.tenantId = :tenantId and"
                    + " r.payrollRun.id = :runId")
    Optional<PayrollApprovalRequest> findForRun(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    @Query(
            "select r.status from PayrollApprovalRequest r where r.tenantId = :tenantId and"
                    + " r.payrollRun.id = :runId")
    Optional<PayrollApprovalRequestStatus> findStatusForRun(
            @Param("tenantId") UUID tenantId, @Param("runId") UUID runId);

    @Query(
            "select r from PayrollApprovalRequest r where r.tenantId = :tenantId and"
                    + " r.companyId = :companyId and r.status = 'PENDING' order by r.submittedAt"
                    + " asc")
    List<PayrollApprovalRequest> findPendingForCompany(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);
}
