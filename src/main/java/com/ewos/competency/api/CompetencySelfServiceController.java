package com.ewos.competency.api;

import com.ewos.competency.api.dto.AssessmentResponse;
import com.ewos.competency.api.dto.CompetencyResponse;
import com.ewos.competency.api.dto.EmployeeCompetencyResponse;
import com.ewos.competency.application.CompetencySelfService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 24D — Employee Self-Service over Competencies. Requires only authentication (no {@code
 * COMPETENCY_*} permission): every method resolves the caller's own employee record and is scoped
 * to it. Mirrors {@code PerformanceSelfServiceController} (Sprint 24A) exactly.
 */
@RestController
@RequestMapping("/api/v1/competencies/self-service")
@Tag(
        name = "Competencies Self-Service",
        description = "An employee's own competency levels, assessments, and the company catalog")
public class CompetencySelfServiceController {

    private final CompetencySelfService service;

    public CompetencySelfServiceController(CompetencySelfService service) {
        this.service = service;
    }

    @GetMapping("/competencies")
    @Operation(summary = "The caller's own assessed competency levels")
    public List<EmployeeCompetencyResponse> myCompetencies() {
        return service.myCompetencies();
    }

    @GetMapping("/assessments")
    @Operation(summary = "The caller's own assessment history")
    public List<AssessmentResponse> myAssessments() {
        return service.myAssessments();
    }

    @GetMapping("/catalog")
    @Operation(summary = "The active competency catalog for the caller's own company")
    public List<CompetencyResponse> competencyCatalog() {
        return service.competencyCatalog();
    }
}
