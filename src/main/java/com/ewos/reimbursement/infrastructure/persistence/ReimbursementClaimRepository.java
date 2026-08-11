package com.ewos.reimbursement.infrastructure.persistence;

import com.ewos.reimbursement.domain.ReimbursementClaim;
import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReimbursementClaimRepository extends JpaRepository<ReimbursementClaim, UUID> {

    Optional<ReimbursementClaim> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndClaimNumber(UUID tenantId, String claimNumber);

    @Query(
            "select c from ReimbursementClaim c where c.tenantId = :tenantId and c.employee.id ="
                    + " :employeeId and (:status is null or c.status = :status) order by"
                    + " c.createdAt desc")
    Page<ReimbursementClaim> findAllForEmployee(
            @Param("tenantId") UUID tenantId,
            @Param("employeeId") UUID employeeId,
            @Param("status") ReimbursementClaimStatus status,
            Pageable pageable);

    /** SUBMITTED claims awaiting a decision from this employee's manager, paginated. */
    @Query(
            "select c from ReimbursementClaim c where c.tenantId = :tenantId and c.status ="
                    + " :status and c.employee.manager.id = :managerId order by c.submittedAt asc")
    Page<ReimbursementClaim> findAllByTenantIdAndStatusAndManagerId(
            @Param("tenantId") UUID tenantId,
            @Param("status") ReimbursementClaimStatus status,
            @Param("managerId") UUID managerId,
            Pageable pageable);

    @Query(
            "select count(c) from ReimbursementClaim c where c.tenantId = :tenantId and c.status ="
                    + " :status and c.employee.manager.id = :managerId")
    long countByTenantIdAndStatusAndManagerId(
            @Param("tenantId") UUID tenantId,
            @Param("status") ReimbursementClaimStatus status,
            @Param("managerId") UUID managerId);

    /** MANAGER_APPROVED claims awaiting a finance decision, scoped to one or more companies. */
    @Query(
            "select c from ReimbursementClaim c where c.tenantId = :tenantId and c.status ="
                    + " :status and c.companyId = :companyId order by c.managerDecisionAt asc")
    Page<ReimbursementClaim> findAllByTenantIdAndStatusAndCompanyId(
            @Param("tenantId") UUID tenantId,
            @Param("status") ReimbursementClaimStatus status,
            @Param("companyId") UUID companyId,
            Pageable pageable);

    List<ReimbursementClaim> findAllByIdInAndTenantId(List<UUID> ids, UUID tenantId);
}
