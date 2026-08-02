package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.TaxDeclarationProof;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaxDeclarationProofRepository extends JpaRepository<TaxDeclarationProof, UUID> {

    Optional<TaxDeclarationProof> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select p from TaxDeclarationProof p where p.tenantId = :tenantId "
                    + "and p.employeeTaxDeclaration.id = :declarationId order by p.uploadedAt desc")
    List<TaxDeclarationProof> findAllForDeclaration(
            @Param("tenantId") UUID tenantId, @Param("declarationId") UUID declarationId);
}
