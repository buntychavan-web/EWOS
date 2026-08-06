package com.ewos.exit.application;

import com.ewos.exit.domain.ExitDocumentTemplate;
import com.ewos.exit.domain.ExitDocumentType;
import com.ewos.exit.domain.Resignation;
import com.ewos.exit.infrastructure.persistence.ResignationRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates exit letter generation (Sprint 26 item 9) on top of {@link
 * ExitDocumentTemplateService} (template resolution) and {@link ExitDocumentPdfGenerationService}
 * (rendering) — same split of responsibilities as Payroll's {@code PayslipPdfService} sitting on
 * {@code PayslipService} + {@code PayslipPdfGenerationService}. Generation is on-demand and
 * stateless: nothing is persisted here, matching how payslip PDFs are (re)generated per download
 * rather than stored as a blob.
 */
@Service
@Transactional(readOnly = true)
public class ExitDocumentGenerationService {

    private final ResignationRepository resignations;
    private final ClientAccessGuard guard;
    private final ExitDocumentTemplateService templates;
    private final ExitDocumentPdfGenerationService generator;

    public ExitDocumentGenerationService(
            ResignationRepository resignations,
            ClientAccessGuard guard,
            ExitDocumentTemplateService templates,
            ExitDocumentPdfGenerationService generator) {
        this.resignations = resignations;
        this.guard = guard;
        this.templates = templates;
        this.generator = generator;
    }

    public byte[] generate(UUID tenantId, UUID resignationId, ExitDocumentType documentType) {
        Resignation r =
                resignations
                        .findByIdAndTenantId(resignationId, tenantId)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND, "Resignation not found"));
        guard.requireAccessForCompany(r.getCompanyId());
        UUID orgUnitId =
                r.getEmployee() == null || r.getEmployee().getPrimaryOrgUnit() == null
                        ? null
                        : r.getEmployee().getPrimaryOrgUnit().getId();
        ExitDocumentTemplate template =
                templates
                        .resolveEffective(tenantId, r.getCompanyId(), orgUnitId, documentType)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.CONFLICT,
                                                "No "
                                                        + documentType
                                                        + " template is configured for this"
                                                        + " company yet — contact an"
                                                        + " administrator"));
        String body = substitute(template.getBodyTemplate(), tokensFor(r));
        return generator.generate(template.getTitle(), body, LocalDate.now());
    }

    private static Map<String, String> tokensFor(Resignation r) {
        Map<String, String> tokens = new HashMap<>();
        var employee = r.getEmployee();
        tokens.put(
                "employeeName", nullToEmpty(employee == null ? null : employee.getDisplayName()));
        tokens.put(
                "employeeNumber",
                nullToEmpty(employee == null ? null : employee.getEmployeeNumber()));
        tokens.put(
                "hireDate",
                employee == null || employee.getHireDate() == null
                        ? ""
                        : employee.getHireDate().toString());
        tokens.put(
                "lastWorkingDate",
                r.getActualLastDay() != null
                        ? r.getActualLastDay().toString()
                        : nullToEmpty(
                                r.getIntendedLastDay() == null
                                        ? null
                                        : r.getIntendedLastDay().toString()));
        tokens.put("noticePeriodDays", String.valueOf(r.getNoticePeriodDays()));
        tokens.put("reason", nullToEmpty(r.getReason()));
        tokens.put(
                "resignationType",
                r.getResignationType() == null ? "" : r.getResignationType().name());
        tokens.put("issueDate", LocalDate.now().toString());
        return tokens;
    }

    private static String substitute(String template, Map<String, String> tokens) {
        String result = template;
        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
