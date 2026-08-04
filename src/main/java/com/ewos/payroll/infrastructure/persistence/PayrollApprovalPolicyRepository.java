package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PayrollApprovalPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollApprovalPolicyRepository
        extends JpaRepository<PayrollApprovalPolicy, UUID> {

    Optional<PayrollApprovalPolicy> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select p from PayrollApprovalPolicy p where p.tenantId = :tenantId and p.companyId ="
                    + " :companyId and p.active = true")
    Optional<PayrollApprovalPolicy> findActiveForCompany(
            @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);
}
