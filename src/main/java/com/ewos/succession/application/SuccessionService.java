package com.ewos.succession.application;

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
import com.ewos.succession.domain.events.SuccessionEvent;
import com.ewos.succession.domain.events.SuccessionEventType;
import com.ewos.succession.infrastructure.persistence.CareerPathRepository;
import com.ewos.succession.infrastructure.persistence.PromotionEligibilityRepository;
import com.ewos.succession.infrastructure.persistence.ReadinessAssessmentRepository;
import com.ewos.succession.infrastructure.persistence.SuccessorCandidateRepository;
import com.ewos.succession.infrastructure.persistence.SuccessorPlanRepository;
import com.ewos.succession.infrastructure.persistence.TalentPoolMemberRepository;
import com.ewos.succession.infrastructure.persistence.TalentPoolRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SuccessionService {

    private final CareerPathRepository careerPaths;
    private final PromotionEligibilityRepository eligibility;
    private final TalentPoolRepository pools;
    private final TalentPoolMemberRepository poolMembers;
    private final SuccessorPlanRepository plans;
    private final SuccessorCandidateRepository candidates;
    private final ReadinessAssessmentRepository readiness;
    private final EmployeeRepository employees;
    private final SuccessionMapper mapper;
    private final ApplicationEventPublisher events;

    public SuccessionService(
            CareerPathRepository careerPaths,
            PromotionEligibilityRepository eligibility,
            TalentPoolRepository pools,
            TalentPoolMemberRepository poolMembers,
            SuccessorPlanRepository plans,
            SuccessorCandidateRepository candidates,
            ReadinessAssessmentRepository readiness,
            EmployeeRepository employees,
            SuccessionMapper mapper,
            ApplicationEventPublisher events) {
        this.careerPaths = careerPaths;
        this.eligibility = eligibility;
        this.pools = pools;
        this.poolMembers = poolMembers;
        this.plans = plans;
        this.candidates = candidates;
        this.readiness = readiness;
        this.employees = employees;
        this.mapper = mapper;
        this.events = events;
    }

    // Career paths -----------------------------------------------------------

    public CareerPathResponse createCareerPath(CreateCareerPathRequest req) {
        if (careerPaths.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Career path code already exists: " + req.code());
        }
        CareerPath c = new CareerPath();
        c.setTenantId(req.tenantId());
        c.setCompanyId(req.companyId());
        c.setCode(req.code());
        c.setName(req.name());
        c.setDescription(req.description());
        c.setFromDesignation(req.fromDesignation());
        c.setToDesignation(req.toDesignation());
        c.setMinTenureMonths(req.minTenureMonths());
        c.setActive(true);
        c = careerPaths.save(c);
        publish(SuccessionEventType.CAREER_PATH_CREATED, c);
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<CareerPathResponse> listCareerPaths(UUID tenantId, UUID companyId) {
        return careerPaths.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CareerPathResponse> listCareerPathsFrom(
            UUID tenantId, UUID companyId, String fromDesignation) {
        return careerPaths
                .findAllByTenantIdAndCompanyIdAndFromDesignationIgnoreCaseAndActiveTrue(
                        tenantId, companyId, fromDesignation)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Promotion eligibility --------------------------------------------------

    public EligibilityResponse recordEligibility(EligibilityRequest req) {
        Employee employee = requireEmployee(req.tenantId(), req.employeeId());
        CareerPath cp =
                req.careerPathId() == null
                        ? null
                        : careerPaths
                                .findByIdAndTenantId(req.careerPathId(), req.tenantId())
                                .orElseThrow(
                                        () ->
                                                new ApiException(
                                                        HttpStatus.BAD_REQUEST,
                                                        "Career path not found"));
        PromotionEligibility e = new PromotionEligibility();
        e.setTenantId(req.tenantId());
        e.setCompanyId(req.companyId());
        e.setEmployee(employee);
        e.setCareerPath(cp);
        e.setEligible(req.eligible());
        e.setTenureMonths(req.tenureMonths());
        e.setLastRating(req.lastRating());
        e.setCompetencyGap(req.competencyGap());
        e.setNotes(req.notes());
        e.setAssessedAt(Instant.now());
        e.setAssessedBy(SuccessionSecurity.currentActor());
        e = eligibility.save(e);
        publishEmp(
                SuccessionEventType.ELIGIBILITY_ASSESSED,
                e.getTenantId(),
                e.getCompanyId(),
                employee.getId(),
                cp == null ? null : cp.getId(),
                null,
                null,
                null,
                req.eligible() ? "ELIGIBLE" : "NOT_ELIGIBLE");
        return mapper.toResponse(e);
    }

    @Transactional(readOnly = true)
    public List<EligibilityResponse> eligibilityHistory(UUID tenantId, UUID employeeId) {
        return eligibility
                .findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(tenantId, employeeId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Talent pools ----------------------------------------------------------

    public PoolResponse createPool(CreatePoolRequest req) {
        if (pools.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(
                req.tenantId(), req.companyId(), req.code())) {
            throw new ApiException(
                    HttpStatus.CONFLICT, "Talent pool code already exists: " + req.code());
        }
        TalentPool p = new TalentPool();
        p.setTenantId(req.tenantId());
        p.setCompanyId(req.companyId());
        p.setCode(req.code());
        p.setName(req.name());
        p.setDescription(req.description());
        p.setTier(req.tier());
        p.setActive(true);
        p = pools.save(p);
        publishPool(SuccessionEventType.POOL_CREATED, p);
        return mapper.toResponse(p);
    }

    @Transactional(readOnly = true)
    public List<PoolResponse> listPools(UUID tenantId, UUID companyId) {
        return pools.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    public PoolMemberResponse addMember(UUID tenantId, UUID poolId, AddMemberRequest req) {
        TalentPool p = requirePool(tenantId, poolId);
        Employee employee = requireEmployee(tenantId, req.employeeId());
        TalentPoolMember m = new TalentPoolMember();
        m.setTenantId(tenantId);
        m.setPool(p);
        m.setEmployee(employee);
        m.setAddedAt(Instant.now());
        m.setAddedBy(SuccessionSecurity.currentActor());
        m.setNotes(req.notes());
        m = poolMembers.save(m);
        publishPool(SuccessionEventType.POOL_MEMBER_ADDED, p);
        return mapper.toResponse(m);
    }

    public PoolMemberResponse removeMember(UUID tenantId, UUID memberId) {
        TalentPoolMember m =
                poolMembers
                        .findByIdAndTenantId(memberId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Pool member not found"));
        m.setRemovedAt(Instant.now());
        if (m.getPool() != null) {
            publishPool(SuccessionEventType.POOL_MEMBER_REMOVED, m.getPool());
        }
        return mapper.toResponse(m);
    }

    @Transactional(readOnly = true)
    public List<PoolMemberResponse> poolMembers(UUID tenantId, UUID poolId) {
        return poolMembers.findAllByTenantIdAndPoolId(tenantId, poolId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Successor plans -------------------------------------------------------

    public PlanResponse createPlan(CreatePlanRequest req) {
        Employee incumbent =
                req.incumbentEmployeeId() == null
                        ? null
                        : requireEmployee(req.tenantId(), req.incumbentEmployeeId());
        SuccessorPlan p = new SuccessorPlan();
        p.setTenantId(req.tenantId());
        p.setCompanyId(req.companyId());
        p.setPositionTitle(req.positionTitle());
        p.setIncumbent(incumbent);
        p.setOrgUnitId(req.orgUnitId());
        p.setNotes(req.notes());
        p = plans.save(p);
        publishPlan(SuccessionEventType.SUCCESSOR_PLAN_CREATED, p);
        return mapper.toResponse(p);
    }

    public CandidateResponse addCandidate(UUID tenantId, UUID planId, AddCandidateRequest req) {
        SuccessorPlan p = requirePlan(tenantId, planId);
        Employee employee = requireEmployee(tenantId, req.employeeId());
        SuccessorCandidate c = new SuccessorCandidate();
        c.setTenantId(tenantId);
        c.setPlan(p);
        c.setEmployee(employee);
        c.setPriority(req.priority());
        c.setReadiness(req.readiness());
        c.setNotes(req.notes());
        c = candidates.save(c);
        publishPlan(SuccessionEventType.SUCCESSOR_CANDIDATE_ADDED, p);
        return mapper.toResponse(c);
    }

    @Transactional(readOnly = true)
    public PlanResponse getPlan(UUID tenantId, UUID id) {
        return mapper.toResponse(requirePlan(tenantId, id));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans(UUID tenantId, UUID companyId) {
        return plans.findAllByTenantIdAndCompanyId(tenantId, companyId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateResponse> candidatesFor(UUID tenantId, UUID planId) {
        return candidates.findAllByTenantIdAndPlanIdOrderByPriorityAsc(tenantId, planId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Readiness assessments -------------------------------------------------

    public ReadinessResponse recordReadiness(ReadinessRequest req) {
        Employee employee = requireEmployee(req.tenantId(), req.employeeId());
        ReadinessAssessment r = new ReadinessAssessment();
        r.setTenantId(req.tenantId());
        r.setCompanyId(req.companyId());
        r.setEmployee(employee);
        r.setPerformanceScore(req.performanceScore());
        r.setPotentialScore(req.potentialScore());
        r.setTier(req.tier());
        r.setReadiness(req.readiness());
        r.setNotes(req.notes());
        r.setAssessedAt(Instant.now());
        r.setAssessedBy(SuccessionSecurity.currentActor());
        r = readiness.save(r);
        publishEmp(
                SuccessionEventType.READINESS_ASSESSED,
                r.getTenantId(),
                r.getCompanyId(),
                employee.getId(),
                null,
                null,
                null,
                r.getId(),
                req.tier() == null ? null : req.tier().name());
        return mapper.toResponse(r);
    }

    @Transactional(readOnly = true)
    public List<ReadinessResponse> readinessHistory(UUID tenantId, UUID employeeId) {
        return readiness
                .findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(tenantId, employeeId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReadinessResponse> readinessByTier(UUID tenantId, UUID companyId, TalentTier tier) {
        return readiness.findAllByTenantIdAndCompanyIdAndTier(tenantId, companyId, tier).stream()
                .map(mapper::toResponse)
                .toList();
    }

    // Dashboard -------------------------------------------------------------

    @Transactional(readOnly = true)
    public SuccessionDashboardResponse dashboard(UUID tenantId, UUID companyId) {
        long paths =
                careerPaths.findAllByTenantIdAndCompanyIdAndActiveTrue(tenantId, companyId).size();
        long promotionEligible =
                eligibility.countByTenantIdAndCompanyIdAndEligibleTrue(tenantId, companyId);
        long hi =
                readiness.countByTenantIdAndCompanyIdAndTier(
                        tenantId, companyId, TalentTier.HIGH_POTENTIAL);
        long hp =
                readiness.countByTenantIdAndCompanyIdAndTier(
                        tenantId, companyId, TalentTier.HIGH_PERFORMER);
        long sc =
                readiness.countByTenantIdAndCompanyIdAndTier(
                        tenantId, companyId, TalentTier.SOLID_CONTRIBUTOR);
        long dev =
                readiness.countByTenantIdAndCompanyIdAndTier(
                        tenantId, companyId, TalentTier.DEVELOPING);
        long un =
                readiness.countByTenantIdAndCompanyIdAndTier(
                        tenantId, companyId, TalentTier.UNDERPERFORMER);
        long readyNow = 0;
        for (SuccessorPlan plan : plans.findAllByTenantIdAndCompanyId(tenantId, companyId)) {
            readyNow +=
                    candidates
                            .findAllByTenantIdAndPlanIdOrderByPriorityAsc(tenantId, plan.getId())
                            .stream()
                            .filter(c -> c.getReadiness() == ReadinessLevel.READY_NOW)
                            .count();
        }
        return new SuccessionDashboardResponse(
                paths, promotionEligible, hi, hp, sc, dev, un, readyNow);
    }

    // Helpers ---------------------------------------------------------------

    private Employee requireEmployee(UUID tenantId, UUID employeeId) {
        return employees
                .findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Employee not found"));
    }

    private TalentPool requirePool(UUID tenantId, UUID id) {
        return pools.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Talent pool not found"));
    }

    private SuccessorPlan requirePlan(UUID tenantId, UUID id) {
        return plans.findByIdAndTenantId(id, tenantId)
                .orElseThrow(
                        () -> new ApiException(HttpStatus.NOT_FOUND, "Successor plan not found"));
    }

    private void publish(SuccessionEventType type, CareerPath c) {
        publishEmp(
                type, c.getTenantId(), c.getCompanyId(), null, c.getId(), null, null, null, null);
    }

    private void publishPool(SuccessionEventType type, TalentPool p) {
        publishEmp(
                type, p.getTenantId(), p.getCompanyId(), null, null, p.getId(), null, null, null);
    }

    private void publishPlan(SuccessionEventType type, SuccessorPlan p) {
        publishEmp(
                type, p.getTenantId(), p.getCompanyId(), null, null, null, p.getId(), null, null);
    }

    private void publishEmp(
            SuccessionEventType type,
            UUID tenantId,
            UUID companyId,
            UUID employeeId,
            UUID careerPathId,
            UUID poolId,
            UUID planId,
            UUID assessmentId,
            String detail) {
        events.publishEvent(
                new SuccessionEvent(
                        type,
                        tenantId,
                        companyId,
                        employeeId,
                        careerPathId,
                        poolId,
                        planId,
                        assessmentId,
                        detail,
                        SuccessionSecurity.currentActor(),
                        Instant.now()));
    }
}
