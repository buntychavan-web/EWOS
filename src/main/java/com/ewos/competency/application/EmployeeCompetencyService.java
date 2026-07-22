package com.ewos.competency.application;

import com.ewos.competency.api.CompetencyMapper;
import com.ewos.competency.api.dto.AssessmentRequest;
import com.ewos.competency.api.dto.AssessmentResponse;
import com.ewos.competency.api.dto.EmployeeCompetencyRequest;
import com.ewos.competency.api.dto.EmployeeCompetencyResponse;
import com.ewos.competency.api.dto.GapReportRow;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.CompetencyAssessment;
import com.ewos.competency.domain.EmployeeCompetency;
import com.ewos.competency.domain.RoleCompetency;
import com.ewos.competency.domain.events.CompetencyEvent;
import com.ewos.competency.domain.events.CompetencyEventType;
import com.ewos.competency.infrastructure.persistence.CompetencyAssessmentRepository;
import com.ewos.competency.infrastructure.persistence.EmployeeCompetencyRepository;
import com.ewos.competency.infrastructure.persistence.RoleCompetencyRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class EmployeeCompetencyService {

    private final EmployeeCompetencyRepository employeeCompetencies;
    private final CompetencyAssessmentRepository assessments;
    private final RoleCompetencyRepository roleCompetencies;
    private final CompetencyService competencies;
    private final EmployeeRepository employees;
    private final CompetencyMapper mapper;
    private final ApplicationEventPublisher events;

    public EmployeeCompetencyService(
            EmployeeCompetencyRepository employeeCompetencies,
            CompetencyAssessmentRepository assessments,
            RoleCompetencyRepository roleCompetencies,
            CompetencyService competencies,
            EmployeeRepository employees,
            CompetencyMapper mapper,
            ApplicationEventPublisher events) {
        this.employeeCompetencies = employeeCompetencies;
        this.assessments = assessments;
        this.roleCompetencies = roleCompetencies;
        this.competencies = competencies;
        this.employees = employees;
        this.mapper = mapper;
        this.events = events;
    }

    public EmployeeCompetencyResponse upsert(EmployeeCompetencyRequest req) {
        Competency c = competencies.require(req.tenantId(), req.competencyId());
        competencies.assertLevelInScale(c, req.currentLevel());
        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        EmployeeCompetency e =
                employeeCompetencies
                        .findByTenantIdAndEmployeeIdAndCompetencyId(
                                req.tenantId(), req.employeeId(), req.competencyId())
                        .orElseGet(EmployeeCompetency::new);
        if (e.getTenantId() == null) {
            e.setTenantId(req.tenantId());
            e.setCompanyId(req.companyId());
            e.setEmployee(employee);
            e.setCompetency(c);
        }
        e.setCurrentLevel(req.currentLevel());
        e.setTargetLevel(req.targetLevel());
        e.setNotes(req.notes());
        e = employeeCompetencies.save(e);
        publish(CompetencyEventType.EMPLOYEE_COMPETENCY_UPDATED, e);
        return mapper.toResponse(e);
    }

    public AssessmentResponse recordAssessment(AssessmentRequest req) {
        Competency c = competencies.require(req.tenantId(), req.competencyId());
        competencies.assertLevelInScale(c, req.assessedLevel());
        Employee employee =
                employees
                        .findByIdAndTenantId(req.employeeId(), req.tenantId())
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        CompetencyAssessment a = new CompetencyAssessment();
        a.setTenantId(req.tenantId());
        a.setCompanyId(req.companyId());
        a.setEmployee(employee);
        a.setCompetency(c);
        a.setAssessmentType(req.assessmentType());
        a.setAssessedLevel(req.assessedLevel());
        a.setAssessedBy(CompetencySecurity.currentActor());
        a.setAssessorName(req.assessorName());
        a.setComments(req.comments());
        a.setAssessedAt(Instant.now());
        a = assessments.save(a);

        EmployeeCompetency current =
                employeeCompetencies
                        .findByTenantIdAndEmployeeIdAndCompetencyId(
                                req.tenantId(), req.employeeId(), req.competencyId())
                        .orElseGet(EmployeeCompetency::new);
        if (current.getTenantId() == null) {
            current.setTenantId(req.tenantId());
            current.setCompanyId(req.companyId());
            current.setEmployee(employee);
            current.setCompetency(c);
            current.setCurrentLevel(req.assessedLevel());
        }
        current.setLastAssessedAt(Instant.now());
        employeeCompetencies.save(current);

        events.publishEvent(
                new CompetencyEvent(
                        CompetencyEventType.ASSESSMENT_RECORDED,
                        a.getTenantId(),
                        a.getCompanyId(),
                        c.getId(),
                        employee.getId(),
                        null,
                        a.getId(),
                        req.assessmentType().name(),
                        CompetencySecurity.currentActor(),
                        Instant.now()));
        return mapper.toResponse(a);
    }

    @Transactional(readOnly = true)
    public List<EmployeeCompetencyResponse> forEmployee(UUID tenantId, UUID employeeId) {
        return employeeCompetencies.findAllByTenantIdAndEmployeeId(tenantId, employeeId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssessmentResponse> assessmentsFor(UUID tenantId, UUID employeeId) {
        return assessments
                .findAllByTenantIdAndEmployeeIdOrderByAssessedAtDesc(tenantId, employeeId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GapReportRow> gapForEmployee(UUID tenantId, UUID employeeId, String designation) {
        Employee employee =
                employees
                        .findByIdAndTenantId(employeeId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST, "Employee not found"));
        List<RoleCompetency> required =
                roleCompetencies.findAllByTenantIdAndCompanyIdAndDesignationIgnoreCase(
                        tenantId, employee.getCompanyId(), designation);
        List<GapReportRow> out = new ArrayList<>();
        for (RoleCompetency r : required) {
            int current =
                    employeeCompetencies
                            .findByTenantIdAndEmployeeIdAndCompetencyId(
                                    tenantId, employeeId, r.getCompetency().getId())
                            .map(EmployeeCompetency::getCurrentLevel)
                            .orElse(0);
            out.add(
                    new GapReportRow(
                            employeeId,
                            employee.getEmployeeNumber(),
                            displayName(employee),
                            r.getCompetency().getId(),
                            r.getCompetency().getCode(),
                            r.getRequiredLevel(),
                            current,
                            Math.max(0, r.getRequiredLevel() - current)));
        }
        return out;
    }

    private String displayName(Employee e) {
        String first = safe(e.getFirstName());
        String last = safe(e.getLastName());
        String combined = (first + " " + last).trim();
        return combined.isEmpty() ? null : combined;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void publish(CompetencyEventType type, EmployeeCompetency e) {
        events.publishEvent(
                new CompetencyEvent(
                        type,
                        e.getTenantId(),
                        e.getCompanyId(),
                        e.getCompetency() == null ? null : e.getCompetency().getId(),
                        e.getEmployee() == null ? null : e.getEmployee().getId(),
                        null,
                        null,
                        null,
                        CompetencySecurity.currentActor(),
                        Instant.now()));
    }
}
