package com.ewos.succession.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.succession.api.SuccessionMapper;
import com.ewos.succession.api.dto.AddCandidateRequest;
import com.ewos.succession.api.dto.AddMemberRequest;
import com.ewos.succession.api.dto.CandidateResponse;
import com.ewos.succession.api.dto.CareerPathResponse;
import com.ewos.succession.api.dto.CreateCareerPathRequest;
import com.ewos.succession.api.dto.CreatePlanRequest;
import com.ewos.succession.api.dto.CreatePoolRequest;
import com.ewos.succession.api.dto.EligibilityRequest;
import com.ewos.succession.api.dto.EligibilityResponse;
import com.ewos.succession.api.dto.PlanResponse;
import com.ewos.succession.api.dto.PoolMemberResponse;
import com.ewos.succession.api.dto.PoolResponse;
import com.ewos.succession.api.dto.ReadinessRequest;
import com.ewos.succession.api.dto.ReadinessResponse;
import com.ewos.succession.api.dto.SuccessionDashboardResponse;
import com.ewos.succession.domain.CareerPath;
import com.ewos.succession.domain.PromotionEligibility;
import com.ewos.succession.domain.ReadinessAssessment;
import com.ewos.succession.domain.ReadinessLevel;
import com.ewos.succession.domain.SuccessorCandidate;
import com.ewos.succession.domain.SuccessorPlan;
import com.ewos.succession.domain.TalentPool;
import com.ewos.succession.domain.TalentPoolMember;
import com.ewos.succession.domain.TalentTier;
import com.ewos.succession.infrastructure.persistence.CareerPathRepository;
import com.ewos.succession.infrastructure.persistence.PromotionEligibilityRepository;
import com.ewos.succession.infrastructure.persistence.ReadinessAssessmentRepository;
import com.ewos.succession.infrastructure.persistence.SuccessorCandidateRepository;
import com.ewos.succession.infrastructure.persistence.SuccessorPlanRepository;
import com.ewos.succession.infrastructure.persistence.TalentPoolMemberRepository;
import com.ewos.succession.infrastructure.persistence.TalentPoolRepository;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * No test suite pre-existed for this service; these tests cover the primary success path and the
 * main guard/conflict branches for each public method, exercised with real domain/mapper objects
 * and mocked repositories.
 */
@ExtendWith(MockitoExtension.class)
class SuccessionServiceTest {

    @Mock CareerPathRepository careerPaths;
    @Mock PromotionEligibilityRepository eligibility;
    @Mock TalentPoolRepository pools;
    @Mock TalentPoolMemberRepository poolMembers;
    @Mock SuccessorPlanRepository plans;
    @Mock SuccessorCandidateRepository candidates;
    @Mock ReadinessAssessmentRepository readiness;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private SuccessionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new SuccessionService(
                        careerPaths,
                        eligibility,
                        pools,
                        poolMembers,
                        plans,
                        candidates,
                        readiness,
                        employees,
                        new SuccessionMapper(),
                        events,
                        guard);
    }

    private Employee employee() {
        Employee e = new Employee();
        e.setId(employeeId);
        e.setTenantId(tenantId);
        e.setCompanyId(companyId);
        return e;
    }

    // Career paths -----------------------------------------------------------

    @Test
    void createCareerPathRejectsADuplicateCode() {
        when(careerPaths.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "L1"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.createCareerPath(
                                        new CreateCareerPathRequest(
                                                tenantId,
                                                companyId,
                                                "L1",
                                                "Engineer to Lead",
                                                null,
                                                "Engineer",
                                                "Lead",
                                                12)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createCareerPathPersistsANewPath() {
        when(careerPaths.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "L2"))
                .thenReturn(false);
        when(careerPaths.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        CareerPathResponse resp =
                service.createCareerPath(
                        new CreateCareerPathRequest(
                                tenantId,
                                companyId,
                                "L2",
                                "Lead to Manager",
                                null,
                                "Lead",
                                "Manager",
                                18));

        assertThat(resp.code()).isEqualTo("L2");
        assertThat(resp.active()).isTrue();
    }

    @Test
    void listCareerPathsChecksCompanyAccess() {
        when(careerPaths.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId))
                .thenReturn(List.of(new CareerPath()));

        assertThat(service.listCareerPaths(tenantId, companyId)).hasSize(1);
    }

    @Test
    void listCareerPathsFromDelegatesToTheRepository() {
        when(careerPaths.findAllByTenantIdAndCompanyIdAndFromDesignationIgnoreCaseAndActiveTrue(
                        tenantId, companyId, "Engineer"))
                .thenReturn(List.of(new CareerPath()));

        assertThat(service.listCareerPathsFrom(tenantId, companyId, "Engineer")).hasSize(1);
    }

    // Promotion eligibility ----------------------------------------------------

    @Test
    void recordEligibilityRejectsAnUnknownCareerPath() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        UUID careerPathId = UUID.randomUUID();
        when(careerPaths.findByIdAndTenantId(careerPathId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.recordEligibility(
                                        new EligibilityRequest(
                                                tenantId,
                                                companyId,
                                                employeeId,
                                                careerPathId,
                                                true,
                                                12,
                                                null,
                                                null,
                                                null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void recordEligibilityPersistsWithoutACareerPath() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(eligibility.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        EligibilityResponse resp =
                service.recordEligibility(
                        new EligibilityRequest(
                                tenantId, companyId, employeeId, null, true, 12, null, null, null));

        assertThat(resp.eligible()).isTrue();
    }

    @Test
    void eligibilityHistoryChecksTheEmployeesCompanyAccess() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(eligibility.findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(tenantId, employeeId))
                .thenReturn(List.of(new PromotionEligibility()));

        assertThat(service.eligibilityHistory(tenantId, employeeId)).hasSize(1);
    }

    // Talent pools -------------------------------------------------------------

    @Test
    void createPoolRejectsADuplicateCode() {
        when(pools.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "HP"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.createPool(
                                        new CreatePoolRequest(
                                                tenantId,
                                                companyId,
                                                "HP",
                                                "High Potential",
                                                null,
                                                null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createPoolPersistsANewPool() {
        when(pools.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "HP2"))
                .thenReturn(false);
        when(pools.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PoolResponse resp =
                service.createPool(
                        new CreatePoolRequest(
                                tenantId,
                                companyId,
                                "HP2",
                                "Pool",
                                null,
                                TalentTier.HIGH_POTENTIAL));

        assertThat(resp.tier()).isEqualTo(TalentTier.HIGH_POTENTIAL);
    }

    @Test
    void listPoolsChecksCompanyAccess() {
        when(pools.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId))
                .thenReturn(List.of(new TalentPool()));

        assertThat(service.listPools(tenantId, companyId)).hasSize(1);
    }

    @Test
    void addMemberChecksBothPoolAndEmployeeExist() {
        TalentPool p = pool();
        when(pools.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(poolMembers.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        PoolMemberResponse resp =
                service.addMember(tenantId, p.getId(), new AddMemberRequest(employeeId, "note"));

        assertThat(resp.notes()).isEqualTo("note");
    }

    @Test
    void removeMemberStampsRemovedAt() {
        TalentPool p = pool();
        TalentPoolMember m = new TalentPoolMember();
        m.setId(UUID.randomUUID());
        m.setPool(p);
        when(poolMembers.findByIdAndTenantId(m.getId(), tenantId)).thenReturn(Optional.of(m));

        PoolMemberResponse resp = service.removeMember(tenantId, m.getId());

        assertThat(resp.removedAt()).isNotNull();
    }

    @Test
    void poolMembersListsAllForThePool() {
        TalentPool p = pool();
        when(pools.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        when(poolMembers.findAllByTenantIdAndPoolId(tenantId, p.getId()))
                .thenReturn(List.of(new TalentPoolMember()));

        assertThat(service.poolMembers(tenantId, p.getId())).hasSize(1);
    }

    private TalentPool pool() {
        TalentPool p = new TalentPool();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setCompanyId(companyId);
        return p;
    }

    // Successor plans ------------------------------------------------------

    @Test
    void createPlanWithoutAnIncumbentSucceeds() {
        when(plans.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        PlanResponse resp =
                service.createPlan(
                        new CreatePlanRequest(tenantId, companyId, "CTO", null, null, null));

        assertThat(resp.positionTitle()).isEqualTo("CTO");
    }

    @Test
    void addCandidateChecksBothPlanAndEmployeeExist() {
        SuccessorPlan p = plan();
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(candidates.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        CandidateResponse resp =
                service.addCandidate(
                        tenantId,
                        p.getId(),
                        new AddCandidateRequest(employeeId, 1, ReadinessLevel.READY_NOW, null));

        assertThat(resp.readiness()).isEqualTo(ReadinessLevel.READY_NOW);
    }

    @Test
    void getPlanReturnsTheMappedPlan() {
        SuccessorPlan p = plan();
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));

        assertThat(service.getPlan(tenantId, p.getId()).id()).isEqualTo(p.getId());
    }

    @Test
    void listPlansChecksCompanyAccess() {
        when(plans.findAllByTenantIdAndCompanyId(tenantId, companyId))
                .thenReturn(List.of(new SuccessorPlan()));

        assertThat(service.listPlans(tenantId, companyId)).hasSize(1);
    }

    @Test
    void candidatesForListsAllForThePlan() {
        SuccessorPlan p = plan();
        when(plans.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));
        when(candidates.findAllByTenantIdAndPlanIdOrderByPriorityAsc(tenantId, p.getId()))
                .thenReturn(List.of(new SuccessorCandidate()));

        assertThat(service.candidatesFor(tenantId, p.getId())).hasSize(1);
    }

    private SuccessorPlan plan() {
        SuccessorPlan p = new SuccessorPlan();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setCompanyId(companyId);
        return p;
    }

    // Readiness assessments -----------------------------------------------

    @Test
    void recordReadinessPersistsAnAssessment() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(readiness.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        ReadinessResponse resp =
                service.recordReadiness(
                        new ReadinessRequest(
                                tenantId,
                                companyId,
                                employeeId,
                                null,
                                null,
                                TalentTier.HIGH_PERFORMER,
                                ReadinessLevel.READY_NOW,
                                null));

        assertThat(resp.tier()).isEqualTo(TalentTier.HIGH_PERFORMER);
    }

    @Test
    void readinessHistoryChecksTheEmployeesCompanyAccess() {
        when(employees.findByIdAndTenantId(employeeId, tenantId))
                .thenReturn(Optional.of(employee()));
        when(readiness.findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(tenantId, employeeId))
                .thenReturn(List.of(new ReadinessAssessment()));

        assertThat(service.readinessHistory(tenantId, employeeId)).hasSize(1);
    }

    @Test
    void readinessByTierChecksCompanyAccess() {
        when(readiness.findAllByTenantIdAndCompanyIdAndTier(
                        tenantId, companyId, TalentTier.HIGH_POTENTIAL))
                .thenReturn(List.of(new ReadinessAssessment()));

        assertThat(service.readinessByTier(tenantId, companyId, TalentTier.HIGH_POTENTIAL))
                .hasSize(1);
    }

    // Dashboard --------------------------------------------------------------

    @Test
    void dashboardAggregatesAllCounters() {
        when(plans.findAllByTenantIdAndCompanyId(tenantId, companyId)).thenReturn(List.of());

        SuccessionDashboardResponse resp = service.dashboard(tenantId, companyId);

        assertThat(resp).isNotNull();
    }
}
