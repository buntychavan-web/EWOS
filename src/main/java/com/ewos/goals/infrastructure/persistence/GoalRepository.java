package com.ewos.goals.infrastructure.persistence;

import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalRepository extends JpaRepository<Goal, UUID> {

    Optional<Goal> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
            UUID tenantId, UUID companyId, String code);

    List<Goal> findAllByTenantIdAndEmployeeId(UUID tenantId, UUID employeeId);

    List<Goal> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, GoalStatus status);

    List<Goal> findAllByTenantIdAndCompanyIdAndScope(
            UUID tenantId, UUID companyId, GoalScope scope);

    List<Goal> findAllByTenantIdAndPerformanceCycleId(UUID tenantId, UUID performanceCycleId);

    /** Sprint 24E — full company goal list, backing CSV export. */
    List<Goal> findAllByTenantIdAndCompanyId(UUID tenantId, UUID companyId);

    long countByTenantIdAndCompanyIdAndStatus(UUID tenantId, UUID companyId, GoalStatus status);

    /**
     * Sprint 24E — active (ASSIGNED/IN_PROGRESS) goals whose {@code period_end} falls within the
     * reminder window, oldest-due-first. Mirrors {@code
     * AppraisalRepository.findOverdueSelfReviews}'s shape.
     */
    @Query(
            "select g from Goal g where g.status in (com.ewos.goals.domain.GoalStatus.ASSIGNED,"
                    + " com.ewos.goals.domain.GoalStatus.IN_PROGRESS) and g.periodEnd is not null"
                    + " and g.periodEnd between :today and :dueBy order by g.periodEnd asc")
    List<Goal> findDueSoon(
            @Param("today") LocalDate today, @Param("dueBy") LocalDate dueBy, Pageable pageable);

    /** See {@link #findDueSoon} — same shape, {@code period_end < today}. */
    @Query(
            "select g from Goal g where g.status in (com.ewos.goals.domain.GoalStatus.ASSIGNED,"
                    + " com.ewos.goals.domain.GoalStatus.IN_PROGRESS) and g.periodEnd is not null"
                    + " and g.periodEnd < :today order by g.periodEnd asc")
    List<Goal> findOverdue(@Param("today") LocalDate today, Pageable pageable);
}
