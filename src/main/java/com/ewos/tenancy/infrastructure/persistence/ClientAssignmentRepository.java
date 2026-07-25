package com.ewos.tenancy.infrastructure.persistence;

import com.ewos.tenancy.domain.ClientAssignment;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientAssignmentRepository extends JpaRepository<ClientAssignment, UUID> {

    List<ClientAssignment> findAllByClientIdOrderByCreatedAtDesc(UUID clientId);

    List<ClientAssignment> findAllByProviderIdOrderByCreatedAtDesc(UUID providerId);

    /**
     * Every currently-active assignment for a user, as of a given date — the Chinese Wall's core
     * query. A row with {@code service} null grants full client access; a row with a specific
     * {@code service} narrows the grant to that one service (not yet consumed at the API layer in
     * 14.1 — {@code service_id} exists so 14.2's Payroll enforcement doesn't need a later
     * migration).
     */
    @Query(
            "select a from ClientAssignment a where a.userId = :userId and a.active = true"
                    + " and a.effectiveFrom <= :asOf"
                    + " and (a.effectiveTo is null or a.effectiveTo >= :asOf)")
    List<ClientAssignment> findActiveForUser(
            @Param("userId") UUID userId, @Param("asOf") LocalDate asOf);
}
