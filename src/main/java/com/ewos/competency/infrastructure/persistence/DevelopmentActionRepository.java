package com.ewos.competency.infrastructure.persistence;

import com.ewos.competency.domain.DevelopmentAction;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DevelopmentActionRepository extends JpaRepository<DevelopmentAction, UUID> {

    Optional<DevelopmentAction> findByIdAndTenantId(UUID id, UUID tenantId);

    List<DevelopmentAction> findAllByTenantIdAndPlanId(UUID tenantId, UUID planId);

    /**
     * Sprint 24E — incomplete actions whose {@code due_on} falls within the reminder window,
     * oldest-due-first. {@code join fetch a.plan p join fetch p.employee} avoids the N+1 the
     * reminder job would otherwise incur resolving each action's owning employee.
     */
    @Query(
            "select a from DevelopmentAction a join fetch a.plan p join fetch p.employee where"
                    + " a.completed = false and a.dueOn is not null and a.dueOn between :today and"
                    + " :dueBy order by a.dueOn asc")
    List<DevelopmentAction> findDueSoon(
            @Param("today") LocalDate today, @Param("dueBy") LocalDate dueBy, Pageable pageable);

    /** See {@link #findDueSoon} — same shape, {@code due_on < today}. */
    @Query(
            "select a from DevelopmentAction a join fetch a.plan p join fetch p.employee where"
                    + " a.completed = false and a.dueOn is not null and a.dueOn < :today order by"
                    + " a.dueOn asc")
    List<DevelopmentAction> findOverdue(@Param("today") LocalDate today, Pageable pageable);
}
