package com.ewos.competency.application;

import com.ewos.competency.api.dto.CreatePlanRequest;
import com.ewos.competency.domain.DevelopmentPlan;
import com.ewos.competency.infrastructure.persistence.DevelopmentPlanRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.importexport.api.dto.ImportRowError;
import com.ewos.importexport.application.ImportJobRecorder;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.io.CsvUtil;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24E — CSV export/template/bulk-import for development plans. Import is deliberately
 * plan-level only (title/description/starts_on/ends_on) — actions are a plan sub-resource added one
 * at a time through {@code DevelopmentPlanController#addAction} and are out of scope for bulk
 * import; a plan created here lands in DRAFT, same as {@link DevelopmentPlanService#create} always
 * produces, and is activated/actioned afterward through the normal UI/API. Every valid row goes
 * through the existing, unmodified {@link DevelopmentPlanService#create}, so import rows get
 * exactly the same validation and event publishing a single admin-tier create call would.
 * Deliberately not {@code @Transactional} itself, for the same partial-success reasoning documented
 * on {@code GoalImportExportService}.
 */
@Service
public class DevelopmentPlanImportExportService {

    private static final String HEADER = "employee_number,title,description,starts_on,ends_on";

    private static final String TEMPLATE =
            HEADER
                    + "\nE0001,Executive presence coaching,Quarterly 1:1 coaching + stretch assignment,"
                    + "2026-01-15,2026-07-15";

    private final DevelopmentPlanService plans;
    private final DevelopmentPlanRepository planRepository;
    private final EmployeeRepository employees;
    private final ImportJobRecorder jobs;

    public DevelopmentPlanImportExportService(
            DevelopmentPlanService plans,
            DevelopmentPlanRepository planRepository,
            EmployeeRepository employees,
            ImportJobRecorder jobs) {
        this.plans = plans;
        this.planRepository = planRepository;
        this.employees = employees;
        this.jobs = jobs;
    }

    public String templateCsv() {
        return TEMPLATE;
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID tenantId, UUID companyId) {
        List<DevelopmentPlan> all =
                planRepository.findAllByTenantIdAndCompanyId(tenantId, companyId);
        Map<UUID, String> employeeNumbers = employeeNumbersFor(all);

        StringBuilder sb = new StringBuilder(256 + all.size() * 128);
        sb.append(HEADER).append('\n');
        for (DevelopmentPlan p : all) {
            sb.append(CsvUtil.escape(employeeNumbers.getOrDefault(employeeId(p), "")))
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(p.getTitle())))
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(p.getDescription())))
                    .append(',')
                    .append(dateOrEmpty(p.getStartsOn()))
                    .append(',')
                    .append(dateOrEmpty(p.getEndsOn()))
                    .append('\n');
        }
        return sb.toString();
    }

    public ImportResultResponse importCsv(
            UUID tenantId, UUID companyId, String fileName, String csvBody) {
        Instant startedAt = Instant.now();
        List<String> lines = CsvUtil.splitLines(csvBody);
        if (lines.isEmpty()) {
            return jobs.record(
                    tenantId, companyId, "DEVELOPMENT_PLAN", fileName, startedAt, 0, List.of());
        }
        List<String> rows = lines.subList(1, lines.size());
        List<ImportRowError> errors = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            int rowNumber = i + 1;
            String line = rows.get(i);
            try {
                importRow(tenantId, companyId, CsvUtil.parseLine(line));
            } catch (ApiException e) {
                errors.add(new ImportRowError(rowNumber, line, e.getMessage()));
            } catch (RuntimeException e) {
                errors.add(new ImportRowError(rowNumber, line, "Invalid row: " + e.getMessage()));
            }
        }

        return jobs.record(
                tenantId, companyId, "DEVELOPMENT_PLAN", fileName, startedAt, rows.size(), errors);
    }

    private void importRow(UUID tenantId, UUID companyId, List<String> fields) {
        if (fields.size() < 2) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Expected at least 2 columns, found " + fields.size());
        }
        String employeeNumber = requireField(fields.get(0), "employee_number");
        String title = requireField(fields.get(1), "title");
        String description = fields.size() > 2 ? CsvUtil.blankToNull(fields.get(2)) : null;
        LocalDate startsOn = fields.size() > 3 ? optionalDate(fields.get(3), "starts_on") : null;
        LocalDate endsOn = fields.size() > 4 ? optionalDate(fields.get(4), "ends_on") : null;

        Employee employee =
                employees
                        .findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                                tenantId, companyId, employeeNumber)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST,
                                                "Unknown employee_number: " + employeeNumber));

        plans.create(
                new CreatePlanRequest(
                        tenantId,
                        companyId,
                        employee.getId(),
                        title,
                        description,
                        startsOn,
                        endsOn));
    }

    private Map<UUID, String> employeeNumbersFor(List<DevelopmentPlan> planList) {
        List<UUID> ids =
                planList.stream()
                        .map(DevelopmentPlanImportExportService::employeeId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (Employee e : employees.findAllById(ids)) {
            out.put(e.getId(), e.getEmployeeNumber());
        }
        return out;
    }

    private static UUID employeeId(DevelopmentPlan p) {
        return p.getEmployee() == null ? null : p.getEmployee().getId();
    }

    private static String requireField(String value, String fieldName) {
        String v = CsvUtil.blankToNull(value);
        if (v == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return v;
    }

    private static LocalDate optionalDate(String value, String fieldName) {
        String v = CsvUtil.blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid " + fieldName + " (expected YYYY-MM-DD): " + v,
                    e);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String dateOrEmpty(LocalDate d) {
        return d == null ? "" : d.toString();
    }
}
