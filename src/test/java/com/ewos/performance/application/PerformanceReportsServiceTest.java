package com.ewos.performance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.api.dto.CalibrationSummaryResponse;
import com.ewos.performance.api.dto.FinalRatingSummaryResponse;
import com.ewos.performance.api.dto.OrgUnitProgressResponse;
import com.ewos.performance.domain.AppraisalStatus;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.BandCount;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.CalibrationAggregate;
import com.ewos.performance.infrastructure.persistence.AppraisalRepository.StatusCount;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerformanceReportsServiceTest {

    @Mock AppraisalRepository appraisals;
    @Mock OrganizationUnitRepository organizationUnits;
    @Mock PerformanceCycleService cycles;
    @Mock ClientAccessGuard guard;

    private PerformanceReportsService service;
    private UUID tenantId;
    private UUID cycleId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        service =
                new PerformanceReportsService(
                        appraisals, organizationUnits, cycles, new PerformanceMapper(), guard);
        tenantId = UUID.randomUUID();
        cycleId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    private PerformanceCycle cycle() {
        PerformanceCycle c = new PerformanceCycle();
        c.setId(cycleId);
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        return c;
    }

    private static OrganizationUnit unit(String code, String name) {
        OrganizationUnit u = new OrganizationUnit();
        u.setId(UUID.randomUUID());
        u.setCode(code);
        u.setName(name);
        return u;
    }

    private static StatusCount statusCount(AppraisalStatus status, long cnt) {
        return new StatusCount() {
            @Override
            public AppraisalStatus getStatus() {
                return status;
            }

            @Override
            public long getCnt() {
                return cnt;
            }
        };
    }

    @Test
    void orgUnitProgressComputesCompletionPercentPerGroup() {
        when(cycles.require(tenantId, cycleId)).thenReturn(cycle());
        OrganizationUnit dept = unit("ENG", "Engineering");
        UUID root = UUID.randomUUID();
        when(organizationUnits.findChildrenOfParent(tenantId, root)).thenReturn(List.of(dept));
        when(organizationUnits.findSelfAndDescendantIds(tenantId, List.of(dept.getId())))
                .thenReturn(List.of(dept.getId()));
        when(appraisals.countByStatusForOrgUnits(tenantId, cycleId, List.of(dept.getId())))
                .thenReturn(
                        List.of(
                                statusCount(AppraisalStatus.FINALISED, 3),
                                statusCount(AppraisalStatus.PENDING_SELF, 1)));

        List<OrgUnitProgressResponse> rows = service.orgUnitProgress(tenantId, cycleId, root);

        assertThat(rows).hasSize(1);
        OrgUnitProgressResponse row = rows.get(0);
        assertThat(row.orgUnitCode()).isEqualTo("ENG");
        assertThat(row.totalAppraisals()).isEqualTo(4);
        assertThat(row.finalised()).isEqualTo(3);
        assertThat(row.pendingSelf()).isEqualTo(1);
        assertThat(row.completionPercent()).isEqualTo(75.0);
    }

    @Test
    void orgUnitProgressReturnsZeroPercentForAnEmptyGroup() {
        when(cycles.require(tenantId, cycleId)).thenReturn(cycle());
        OrganizationUnit empty = unit("HR", "Human Resources");
        when(organizationUnits.findAllByTenantIdAndCompanyIdAndParentIsNullOrderByCodeAsc(
                        tenantId, companyId))
                .thenReturn(List.of(empty));
        when(organizationUnits.findSelfAndDescendantIds(tenantId, List.of(empty.getId())))
                .thenReturn(List.of(empty.getId()));
        when(appraisals.countByStatusForOrgUnits(tenantId, cycleId, List.of(empty.getId())))
                .thenReturn(List.of());

        List<OrgUnitProgressResponse> rows = service.orgUnitProgress(tenantId, cycleId, null);

        assertThat(rows.get(0).totalAppraisals()).isZero();
        assertThat(rows.get(0).completionPercent()).isZero();
    }

    @Test
    void calibrationSummaryDefaultsAverageDeltaToZeroWhenNoRowsQualify() {
        when(cycles.require(tenantId, cycleId)).thenReturn(cycle());
        CalibrationAggregate agg =
                new CalibrationAggregate() {
                    @Override
                    public long getTotalCalibrated() {
                        return 0;
                    }

                    @Override
                    public long getAdjustedUp() {
                        return 0;
                    }

                    @Override
                    public long getAdjustedDown() {
                        return 0;
                    }

                    @Override
                    public long getUnchanged() {
                        return 0;
                    }

                    @Override
                    public BigDecimal getAverageDelta() {
                        return null;
                    }
                };
        when(appraisals.calibrationSummary(tenantId, cycleId)).thenReturn(agg);

        CalibrationSummaryResponse response = service.calibrationSummary(tenantId, cycleId);

        assertThat(response.averageDelta()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void finalRatingSummaryMapsNullBandToUnbanded() {
        when(cycles.require(tenantId, cycleId)).thenReturn(cycle());
        BandCount unbanded =
                new BandCount() {
                    @Override
                    public String getBand() {
                        return null;
                    }

                    @Override
                    public long getCnt() {
                        return 2;
                    }

                    @Override
                    public BigDecimal getAverageRating() {
                        return BigDecimal.valueOf(3.5);
                    }
                };
        when(appraisals.finalRatingByBand(tenantId, cycleId)).thenReturn(List.of(unbanded));
        when(appraisals.finalRatingByIncrementRecommendation(tenantId, cycleId))
                .thenReturn(List.of());
        when(appraisals.finalRatingByPromotionRecommendation(tenantId, cycleId))
                .thenReturn(List.of());

        FinalRatingSummaryResponse response = service.finalRatingSummary(tenantId, cycleId);

        assertThat(response.totalFinalised()).isEqualTo(2);
        assertThat(response.byBand()).hasSize(1);
        assertThat(response.byBand().get(0).band()).isEqualTo("UNBANDED");
    }
}
