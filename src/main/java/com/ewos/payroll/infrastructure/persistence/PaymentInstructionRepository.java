package com.ewos.payroll.infrastructure.persistence;

import com.ewos.payroll.domain.PaymentInstruction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentInstructionRepository extends JpaRepository<PaymentInstruction, UUID> {

    Optional<PaymentInstruction> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query(
            "select p from PaymentInstruction p where p.tenantId = :tenantId "
                    + "and p.bankAdvice.id = :adviceId order by p.employee.employeeNumber asc")
    List<PaymentInstruction> findAllForAdvice(
            @Param("tenantId") UUID tenantId, @Param("adviceId") UUID adviceId);
}
