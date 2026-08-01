package com.ewos.performance.application;

import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.api.dto.AppraisalReportRowResponse;
import com.ewos.performance.api.dto.CalibrationSummaryResponse;
import com.ewos.performance.api.dto.FinalRatingSummaryResponse;
import com.ewos.performance.api.dto.OrgUnitProgressResponse;
import com.ewos.performance.api.dto.RatingDistributionBucketResponse;
import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.BandCount;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.CalibrationAggregate;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.RecommendationCount;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.StatusCount;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24B — read-only reporting surface over a performance cycle. Every method is {@code
 * PERF_READ}-gated at the controller and company-scoped here via {@link ClientAccessGuard}, same as
 * every other read path in this module. "Department Progress" and "Business Unit Progress" from the
 * sprint brief are the same underlying report ({@link #orgUnitProgress}) called with a different
 * {@code rootOrgUnitId} — department and business unit are not distinct concepts in this platform's
 * org model, both are just {@code OrganizationUnit} nodes (see {@code
 * LaunchAppraisalCycleRequest}'s javadoc for the same point). "Company Progress" is likewise not a
 * separate query: a cycle belongs to exactly one company, so it is identical to the existing
 * Overall Cycle Dashboard ({@link AppraisalService#dashboard}) and is not duplicated here.
 */
@Service
public class PerformanceReportsService {

    private final AppraisalRepository appraisals;
    private final OrganizationUnitRepository organizationUnits;
    private final PerformanceCycleService cycles;
    private final PerformanceMapper mapper;
    private final ClientAccessGuard guard;

    public PerformanceReportsService(
            AppraisalRepository appraisals,
            OrganizationUnitRepository organizationUnits,
            PerformanceCycleService cycles,
            PerformanceMapper mapper,
            ClientAccessGuard guard) {
        this.appraisals = appraisals;
        this.organizationUnits = organizationUnits;
        this.cycles = cycles;
        this.mapper = mapper;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public Page<AppraisalReportRowResponse> pendingSelfReviews(
            UUID tenantId, UUID cycleId, Pageable pageable) {
        return statusPage(tenantId, cycleId, AppraisalStatus.PENDING_SELF, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AppraisalReportRowResponse> pendingManagerReviews(
            UUID tenantId, UUID cycleId, Pageable pageable) {
        return statusPage(tenantId, cycleId, AppraisalStatus.PENDING_MANAGER, pageable);
    }

    @Transactional(readOnly = true)
    public Page<AppraisalReportRowResponse> pendingReviewerReviews(
            UUID tenantId, UUID cycleId, Pageable pageable) {
        return statusPage(tenantId, cycleId, AppraisalStatus.PENDING_REVIEWER, pageable);
    }

    private Page<AppraisalReportRowResponse> statusPage(
            UUID tenantId, UUID cycleId, AppraisalStatus status, Pageable pageable) {
        PerformanceCycle cycle = cycles.require(tenantId, cycleId);
        guard.requireAccessForCompany(cycle.getCompanyId());
        return appraisals
                .findAllByTenantIdAndCycleIdAndStatus(tenantId, cycleId, status, pageable)
                .map(mapper::toReportRow);
    }

    @Transactional(readOnly = true)
    public Page<AppraisalReportRowResponse> employeeStatusReport(
            UUID tenantId,
            UUID cycleId,
            AppraisalStatus status,
            UUID orgUnitId,
            boolean includeDescendants,
            Pageable pageable) {
        PerformanceCycle cycle = cycles.require(tenantId, cycleId);
        guard.requireAccessForCompany(cycle.getCompanyId());
        List<UUID> orgUnitIds = resolveOrgUnitIds(tenantId, orgUnitId, includeDescendants);
        return appraisals
                .findEmployeeStatusReport(tenantId, cycleId, status, orgUnitIds, pageable)
                .map(mapper::toReportRow);
    }

    /**
     * Serves both "Department Progress" and "Business Unit Progress": one row per direct child of
     * {@code rootOrgUnitId} (or every top-level unit in the company if {@code rootOrgUnitId} is
     * null), each counting every appraisal belonging to an employee anywhere in that child's
     * subtree — not just employees directly assigned to the child node itself, since real org
     * charts put employees on leaf nodes (teams/departments), not on the intermediate
     * business-unit/division nodes a caller is likely asking to roll up to.
     */
    @Transactional(readOnly = true)
    public List<OrgUnitProgressResponse> orgUnitProgress(
            UUID tenantId, UUID cycleId, UUID rootOrgUnitId) {
        PerformanceCycle cycle = cycles.require(tenantId, cycleId);
        guard.requireAccessForCompany(cycle.getCompanyId());

        List<OrganizationUnit> groups =
                rootOrgUnitId == null
                        ? organizationUnits
                                .findAllByTenantIdAndCompanyIdAndParentIsNullOrderByCodeAsc(
                                        tenantId, cycle.getCompanyId())
                        : organizationUnits.findChildrenOfParent(tenantId, rootOrgUnitId);

        return groups.stream().map(g -> orgUnitProgressRow(tenantId, cycleId, g)).toList();
    }

    private OrgUnitProgressResponse orgUnitProgressRow(
            UUID tenantId, UUID cycleId, OrganizationUnit group) {
        List<UUID> subtree =
                organizationUnits.findSelfAndDescendantIds(tenantId, List.of(group.getId()));
        Map<AppraisalStatus, Long> byStatus = new EnumMap<>(AppraisalStatus.class);
        for (StatusCount sc : appraisals.countByStatusForOrgUnits(tenantId, cycleId, subtree)) {
            byStatus.put(sc.getStatus(), sc.getCnt());
        }
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long finalised = byStatus.getOrDefault(AppraisalStatus.FINALISED, 0L);
        double completionPercent = total == 0 ? 0.0 : finalised * 100.0 / total;
        return new OrgUnitProgressResponse(
                group.getId(),
                group.getCode(),
                group.getName(),
                total,
                byStatus.getOrDefault(AppraisalStatus.PENDING_SELF, 0L),
                byStatus.getOrDefault(AppraisalStatus.PENDING_MANAGER, 0L),
                byStatus.getOrDefault(AppraisalStatus.PENDING_REVIEWER, 0L),
                byStatus.getOrDefault(AppraisalStatus.CALIBRATION, 0L),
                byStatus.getOrDefault(AppraisalStatus.PENDING_APPROVAL, 0L),
                finalised,
                byStatus.getOrDefault(AppraisalStatus.CANCELLED, 0L),
                completionPercent);
    }

    @Transactional(readOnly = true)
    public List<RatingDistributionBucketResponse> ratingDistribution(UUID tenantId, UUID cycleId) {
        PerformanceCycle cycle = cycles.require(tenantId, cycleId);
        guard.requireAccessForCompany(cycle.getCompanyId());
        return appraisals.ratingDistribution(tenantId, cycleId).stream()
                .map(r -> new RatingDistributionBucketResponse(r.getRatingBucket(), r.getCnt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CalibrationSummaryResponse calibrationSummary(UUID tenantId, UUID cycleId) {
        PerformanceCycle cycle = cycles.require(tenantId, cycleId);
        guard.requireAccessForCompany(cycle.getCompanyId());
        CalibrationAggregate agg = appraisals.calibrationSummary(tenantId, cycleId);
        return new CalibrationSummaryResponse(
                agg.getTotalCalibrated(),
                agg.getAdjustedUp(),
                agg.getAdjustedDown(),
                agg.getUnchanged(),
                agg.getAverageDelta() == null ? BigDecimal.ZERO : agg.getAverageDelta());
    }

    @Transactional(readOnly = true)
    public FinalRatingSummaryResponse finalRatingSummary(UUID tenantId, UUID cycleId) {
        PerformanceCycle cycle = cycles.require(tenantId, cycleId);
        guard.requireAccessForCompany(cycle.getCompanyId());

        List<BandCount> bandCounts = appraisals.finalRatingByBand(tenantId, cycleId);
        long total = bandCounts.stream().mapToLong(BandCount::getCnt).sum();
        List<FinalRatingSummaryResponse.BandCount> byBand =
                bandCounts.stream()
                        .map(
                                b ->
                                        new FinalRatingSummaryResponse.BandCount(
                                                b.getBand() == null ? "UNBANDED" : b.getBand(),
                                                b.getCnt(),
                                                b.getAverageRating()))
                        .toList();

        Map<String, Long> byIncrement = new LinkedHashMap<>();
        for (RecommendationCount rc :
                appraisals.finalRatingByIncrementRecommendation(tenantId, cycleId)) {
            byIncrement.put(
                    rc.getRecommendation() == null ? "NONE" : rc.getRecommendation().toString(),
                    rc.getCnt());
        }
        Map<String, Long> byPromotion = new LinkedHashMap<>();
        for (RecommendationCount rc :
                appraisals.finalRatingByPromotionRecommendation(tenantId, cycleId)) {
            byPromotion.put(
                    rc.getRecommendation() == null ? "NONE" : rc.getRecommendation().toString(),
                    rc.getCnt());
        }

        return new FinalRatingSummaryResponse(total, byBand, byIncrement, byPromotion);
    }

    /**
     * {@code null} (no filter) and an empty list (filter to nobody) are different, load-bearing
     * outcomes for {@code AppraisalRepository.findEmployeeStatusReport}; this intentionally does
     * not follow the usual "return empty rather than null" convention.
     */
    @SuppressWarnings("PMD.ReturnEmptyCollectionRatherThanNull")
    private List<UUID> resolveOrgUnitIds(
            UUID tenantId, UUID orgUnitId, boolean includeDescendants) {
        if (orgUnitId == null) {
            return null;
        }
        return includeDescendants
                ? organizationUnits.findSelfAndDescendantIds(tenantId, List.of(orgUnitId))
                : List.of(orgUnitId);
    }
}
