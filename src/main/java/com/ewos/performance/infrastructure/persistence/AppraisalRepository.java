package com.ewos.performance.infrastructure.persistence;

import com.ewos.performance.domain.Appraisal;
import com.ewos.performance.domain.AppraisalStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppraisalRepository extends JpaRepository<Appraisal, UUID> {

    Optional<Appraisal> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Scalar-only projection (no entity hydration, so no lazy-loading trap for a caller running
     * outside the repository's own transaction — see {@code PerformanceNotificationEventListener},
     * which reads this after a {@code @TransactionalEventListener(AFTER_COMMIT)} boundary).
     */
    @Query(
            "select a.employee.userId as employeeUserId, a.managerEmployee.userId as"
                    + " managerUserId, a.reviewerEmployee.userId as reviewerUserId from Appraisal a"
                    + " where a.id = :id and a.tenantId = :tenantId")
    Optional<AppraisalParticipantUserIds> findParticipantUserIds(
            @Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /** See {@link #findParticipantUserIds}. */
    interface AppraisalParticipantUserIds {
        UUID getEmployeeUserId();

        UUID getManagerUserId();

        UUID getReviewerUserId();
    }

    Optional<Appraisal> findByTenantIdAndCycleIdAndEmployeeId(
            UUID tenantId, UUID cycleId, UUID employeeId);

    List<Appraisal> findAllByTenantIdAndCycleIdAndStatus(
            UUID tenantId, UUID cycleId, AppraisalStatus status);

    List<Appraisal> findAllByTenantIdAndCycleId(UUID tenantId, UUID cycleId);

    List<Appraisal> findAllByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, AppraisalStatus status);

    List<Appraisal> findAllByTenantIdAndEmployeeIdOrderByCreatedAtDesc(
            UUID tenantId, UUID employeeId);

    List<Appraisal> findAllByTenantIdAndManagerEmployeeIdAndStatus(
            UUID tenantId, UUID managerEmployeeId, AppraisalStatus status);

    List<Appraisal> findAllByTenantIdAndReviewerEmployeeIdAndStatus(
            UUID tenantId, UUID reviewerEmployeeId, AppraisalStatus status);

    long countByTenantIdAndCycleIdAndStatus(UUID tenantId, UUID cycleId, AppraisalStatus status);

    long countByTenantIdAndCompanyIdAndStatus(
            UUID tenantId, UUID companyId, AppraisalStatus status);

    /**
     * Sprint 24B — which of {@code employeeIds} already have an appraisal in {@code cycleId}. The
     * per-chunk existence check behind bulk launch's idempotency: re-running an overlapping launch
     * only ever fills the gap, never duplicates ({@code ux_appraisals_cycle_employee_alive} is the
     * hard guarantee; this query is what lets the chunk processor skip the conflict cheaply instead
     * of hitting it as an exception per row).
     */
    @Query(
            "select a.employee.id from Appraisal a where a.tenantId = :tenantId and a.cycle.id ="
                    + " :cycleId and a.employee.id in :employeeIds")
    List<UUID> findAllByTenantIdAndCycleIdAndEmployeeIdIn(
            @Param("tenantId") UUID tenantId,
            @Param("cycleId") UUID cycleId,
            @Param("employeeIds") List<UUID> employeeIds);

    /**
     * Sprint 24B — appraisals still {@code PENDING_SELF} past their cycle's {@code
     * self_assessment_due}, oldest overdue first, capped by {@code pageable} so one reminder sweep
     * is always bounded work regardless of backlog size. Backs {@code PerformanceReminderJob}.
     * {@code join fetch cycle} avoids a second lookup for {@code tenantId}/{@code companyId} per
     * row when the job publishes an event off each one.
     */
    @Query(
            "select a from Appraisal a join fetch a.cycle join fetch a.employee where a.status ="
                    + " com.ewos.performance.domain.AppraisalStatus.PENDING_SELF and"
                    + " a.cycle.selfAssessmentDue is not null and a.cycle.selfAssessmentDue <"
                    + " :today order by a.cycle.selfAssessmentDue asc")
    List<Appraisal> findOverdueSelfReviews(@Param("today") LocalDate today, Pageable pageable);

    /** See {@link #findOverdueSelfReviews} — same shape, {@code manager_assessment_due}. */
    @Query(
            "select a from Appraisal a join fetch a.cycle join fetch a.employee where a.status ="
                    + " com.ewos.performance.domain.AppraisalStatus.PENDING_MANAGER and"
                    + " a.cycle.managerAssessmentDue is not null and a.cycle.managerAssessmentDue <"
                    + " :today order by a.cycle.managerAssessmentDue asc")
    List<Appraisal> findOverdueManagerReviews(@Param("today") LocalDate today, Pageable pageable);

    /** See {@link #findOverdueSelfReviews} — same shape, {@code reviewer_assessment_due}. */
    @Query(
            "select a from Appraisal a join fetch a.cycle join fetch a.employee where a.status ="
                    + " com.ewos.performance.domain.AppraisalStatus.PENDING_REVIEWER and"
                    + " a.cycle.reviewerAssessmentDue is not null and"
                    + " a.cycle.reviewerAssessmentDue < :today order by"
                    + " a.cycle.reviewerAssessmentDue asc")
    List<Appraisal> findOverdueReviewerReviews(@Param("today") LocalDate today, Pageable pageable);

    /**
     * Sprint 24B — paginated variant of {@link #findAllByTenantIdAndCycleIdAndStatus(UUID, UUID,
     * AppraisalStatus)}, backing the Pending Self/Manager/Reviewer Review reports.
     */
    Page<Appraisal> findAllByTenantIdAndCycleIdAndStatus(
            UUID tenantId, UUID cycleId, AppraisalStatus status, Pageable pageable);

    /**
     * Sprint 24B — Employee Status Report: every appraisal in a cycle, optionally narrowed by
     * status and/or org-unit subtree, paginated/sorted by the caller's {@link Pageable}.
     */
    @Query(
            "select a from Appraisal a where a.tenantId = :tenantId and a.cycle.id = :cycleId and"
                    + " (:status is null or a.status = :status) and (:orgUnitIds is null or"
                    + " a.employee.primaryOrgUnit.id in :orgUnitIds)")
    Page<Appraisal> findEmployeeStatusReport(
            @Param("tenantId") UUID tenantId,
            @Param("cycleId") UUID cycleId,
            @Param("status") AppraisalStatus status,
            @Param("orgUnitIds") List<UUID> orgUnitIds,
            Pageable pageable);

    /**
     * Sprint 24B — org-unit progress report: per-status appraisal counts across a set of (already
     * subtree-expanded) org unit ids.
     */
    @Query(
            "select a.status as status, count(a) as cnt from Appraisal a where a.tenantId ="
                    + " :tenantId and a.cycle.id = :cycleId and a.employee.primaryOrgUnit.id in"
                    + " :orgUnitIds group by a.status")
    List<StatusCount> countByStatusForOrgUnits(
            @Param("tenantId") UUID tenantId,
            @Param("cycleId") UUID cycleId,
            @Param("orgUnitIds") List<UUID> orgUnitIds);

    /** See {@link #countByStatusForOrgUnits}. */
    interface StatusCount {
        AppraisalStatus getStatus();

        long getCnt();
    }

    /**
     * Sprint 24B — Rating Distribution: finalised appraisals bucketed by whole-number final rating.
     */
    @Query(
            "select cast(floor(a.finalRating) as integer) as ratingBucket, count(a) as cnt from"
                    + " Appraisal a where a.tenantId = :tenantId and a.cycle.id = :cycleId and"
                    + " a.status = com.ewos.performance.domain.AppraisalStatus.FINALISED and"
                    + " a.finalRating is not null group by cast(floor(a.finalRating) as integer)"
                    + " order by ratingBucket")
    List<RatingBucketCount> ratingDistribution(
            @Param("tenantId") UUID tenantId, @Param("cycleId") UUID cycleId);

    /** See {@link #ratingDistribution}. */
    interface RatingBucketCount {
        int getRatingBucket();

        long getCnt();
    }

    /**
     * Sprint 24B — Calibration Summary: a single DB-side aggregate (never pulls per-appraisal rows
     * into the app) comparing each appraisal's reviewer rating to its calibrated rating.
     */
    @Query(
            "select count(a) as totalCalibrated, coalesce(sum(case when a.calibratedRating >"
                    + " a.reviewerRating then 1 else 0 end), 0) as adjustedUp, coalesce(sum(case"
                    + " when a.calibratedRating < a.reviewerRating then 1 else 0 end), 0) as"
                    + " adjustedDown, coalesce(sum(case when a.calibratedRating = a.reviewerRating"
                    + " then 1 else 0 end), 0) as unchanged, avg(a.calibratedRating -"
                    + " a.reviewerRating) as averageDelta from Appraisal a where a.tenantId ="
                    + " :tenantId and a.cycle.id = :cycleId and a.calibratedRating is not null and"
                    + " a.reviewerRating is not null")
    CalibrationAggregate calibrationSummary(
            @Param("tenantId") UUID tenantId, @Param("cycleId") UUID cycleId);

    /** See {@link #calibrationSummary}. */
    interface CalibrationAggregate {
        long getTotalCalibrated();

        long getAdjustedUp();

        long getAdjustedDown();

        long getUnchanged();

        BigDecimal getAverageDelta();
    }

    /**
     * Sprint 24B — Final Rating Summary: finalised-appraisal counts and average rating per band.
     */
    @Query(
            "select a.finalBand as band, count(a) as cnt, avg(a.finalRating) as averageRating from"
                    + " Appraisal a where a.tenantId = :tenantId and a.cycle.id = :cycleId and"
                    + " a.status = com.ewos.performance.domain.AppraisalStatus.FINALISED group by"
                    + " a.finalBand")
    List<BandCount> finalRatingByBand(
            @Param("tenantId") UUID tenantId, @Param("cycleId") UUID cycleId);

    /** See {@link #finalRatingByBand}. */
    interface BandCount {
        String getBand();

        long getCnt();

        BigDecimal getAverageRating();
    }

    /** Sprint 24B — increment-recommendation breakdown among finalised appraisals. */
    @Query(
            "select a.incrementRecommendation as recommendation, count(a) as cnt from Appraisal a"
                    + " where a.tenantId = :tenantId and a.cycle.id = :cycleId and a.status ="
                    + " com.ewos.performance.domain.AppraisalStatus.FINALISED group by"
                    + " a.incrementRecommendation")
    List<RecommendationCount> finalRatingByIncrementRecommendation(
            @Param("tenantId") UUID tenantId, @Param("cycleId") UUID cycleId);

    /** Sprint 24B — promotion-recommendation breakdown among finalised appraisals. */
    @Query(
            "select a.promotionRecommendation as recommendation, count(a) as cnt from Appraisal a"
                    + " where a.tenantId = :tenantId and a.cycle.id = :cycleId and a.status ="
                    + " com.ewos.performance.domain.AppraisalStatus.FINALISED group by"
                    + " a.promotionRecommendation")
    List<RecommendationCount> finalRatingByPromotionRecommendation(
            @Param("tenantId") UUID tenantId, @Param("cycleId") UUID cycleId);

    /**
     * See {@link #finalRatingByIncrementRecommendation}/{@link
     * #finalRatingByPromotionRecommendation}.
     */
    interface RecommendationCount {
        Object getRecommendation();

        long getCnt();
    }
}
