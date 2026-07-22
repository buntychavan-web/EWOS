package com.ewos.competency.api;

import com.ewos.competency.api.dto.AssessmentRequest;
import com.ewos.competency.api.dto.AssessmentResponse;
import com.ewos.competency.api.dto.EmployeeCompetencyRequest;
import com.ewos.competency.api.dto.EmployeeCompetencyResponse;
import com.ewos.competency.api.dto.GapReportRow;
import com.ewos.competency.api.dto.RoleCompetencyRequest;
import com.ewos.competency.api.dto.RoleCompetencyResponse;
import com.ewos.competency.application.EmployeeCompetencyService;
import com.ewos.competency.application.RoleCompetencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/competency-matrix")
@Tag(
        name = "Competency Matrix",
        description = "Role competencies + employee competencies + assessments")
public class CompetencyMatrixController {

    private final RoleCompetencyService roleCompetencies;
    private final EmployeeCompetencyService employeeCompetencies;

    public CompetencyMatrixController(
            RoleCompetencyService roleCompetencies,
            EmployeeCompetencyService employeeCompetencies) {
        this.roleCompetencies = roleCompetencies;
        this.employeeCompetencies = employeeCompetencies;
    }

    @PostMapping("/role")
    @PreAuthorize("hasAuthority('COMPETENCY_WRITE')")
    @Operation(summary = "Set (create) a role competency requirement")
    public ResponseEntity<RoleCompetencyResponse> setRoleCompetency(
            @Valid @RequestBody RoleCompetencyRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(roleCompetencies.set(req));
    }

    @GetMapping("/role/by-designation")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List required competencies for a designation")
    public List<RoleCompetencyResponse> forDesignation(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam String designation) {
        return roleCompetencies.forDesignation(tenantId, companyId, designation);
    }

    @GetMapping("/role/by-org-unit")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List required competencies for an org unit")
    public List<RoleCompetencyResponse> forOrgUnit(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @RequestParam UUID companyId,
            @RequestParam UUID orgUnitId) {
        return roleCompetencies.forOrgUnit(tenantId, companyId, orgUnitId);
    }

    @PutMapping("/employee")
    @PreAuthorize("hasAuthority('COMPETENCY_WRITE')")
    @Operation(summary = "Upsert an employee's current + target competency level")
    public EmployeeCompetencyResponse upsertEmployeeCompetency(
            @Valid @RequestBody EmployeeCompetencyRequest req) {
        return employeeCompetencies.upsert(req);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List an employee's competencies")
    public List<EmployeeCompetencyResponse> forEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return employeeCompetencies.forEmployee(tenantId, employeeId);
    }

    @PostMapping("/assess")
    @PreAuthorize("hasAuthority('COMPETENCY_ASSESS')")
    @Operation(summary = "Record a competency assessment")
    public ResponseEntity<AssessmentResponse> assess(@Valid @RequestBody AssessmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeCompetencies.recordAssessment(req));
    }

    @GetMapping("/assessments/{employeeId}")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "List an employee's assessment history")
    public List<AssessmentResponse> assessmentsFor(
            @RequestHeader("X-Tenant-Id") UUID tenantId, @PathVariable UUID employeeId) {
        return employeeCompetencies.assessmentsFor(tenantId, employeeId);
    }

    @GetMapping("/gap/{employeeId}")
    @PreAuthorize("hasAuthority('COMPETENCY_READ')")
    @Operation(summary = "Gap analysis for an employee against a designation")
    public List<GapReportRow> gapForEmployee(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable UUID employeeId,
            @RequestParam String designation) {
        return employeeCompetencies.gapForEmployee(tenantId, employeeId, designation);
    }
}
