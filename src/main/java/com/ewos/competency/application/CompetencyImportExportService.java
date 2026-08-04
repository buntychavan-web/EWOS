package com.ewos.competency.application;

import com.ewos.competency.api.dto.EmployeeCompetencyRequest;
import com.ewos.competency.domain.Competency;
import com.ewos.competency.domain.EmployeeCompetency;
import com.ewos.competency.infrastructure.persistence.CompetencyRepository;
import com.ewos.competency.infrastructure.persistence.EmployeeCompetencyRepository;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.importexport.api.dto.ImportRowError;
import com.ewos.importexport.application.ImportJobRecorder;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.io.CsvUtil;
import java.time.Instant;
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
 * Sprint 24E — CSV export/template/bulk-import for employee competency levels (the per-employee
 * current/target rating against a competency, i.e. {@link EmployeeCompetency}, not the reusable
 * {@link Competency} catalog itself — that library is small and hand-curated via {@code
 * CompetencyController}, whereas employee ratings are the bulk, per-headcount data an HR admin
 * would actually want to import/export in volume). Every valid row is created through the existing,
 * unmodified {@link EmployeeCompetencyService#upsert}, so import rows go through exactly the same
 * validation (scale bounds, tenant/company checks) and event publishing a single admin-tier upsert
 * call would. Deliberately not {@code @Transactional} itself, for the same partial-success
 * reasoning documented on {@code GoalImportExportService}.
 */
@Service
public class CompetencyImportExportService {

    private static final String HEADER =
            "employee_number,competency_code,current_level,target_level,notes";

    private static final String TEMPLATE =
            HEADER
                    + "\nE0001,COMM-01,3,4,Strong written communication; needs more executive presence";

    private final EmployeeCompetencyService employeeCompetencies;
    private final EmployeeCompetencyRepository employeeCompetencyRepository;
    private final CompetencyRepository competencyRepository;
    private final EmployeeRepository employees;
    private final ImportJobRecorder jobs;

    public CompetencyImportExportService(
            EmployeeCompetencyService employeeCompetencies,
            EmployeeCompetencyRepository employeeCompetencyRepository,
            CompetencyRepository competencyRepository,
            EmployeeRepository employees,
            ImportJobRecorder jobs) {
        this.employeeCompetencies = employeeCompetencies;
        this.employeeCompetencyRepository = employeeCompetencyRepository;
        this.competencyRepository = competencyRepository;
        this.employees = employees;
        this.jobs = jobs;
    }

    public String templateCsv() {
        return TEMPLATE;
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID tenantId, UUID companyId) {
        List<EmployeeCompetency> all =
                employeeCompetencyRepository.findAllByTenantIdAndCompanyId(tenantId, companyId);
        Map<UUID, String> employeeNumbers = employeeNumbersFor(all);
        Map<UUID, String> competencyCodes = competencyCodesFor(all);

        StringBuilder sb = new StringBuilder(256 + all.size() * 96);
        sb.append(HEADER).append('\n');
        for (EmployeeCompetency e : all) {
            sb.append(CsvUtil.escape(employeeNumbers.getOrDefault(employeeId(e), "")))
                    .append(',')
                    .append(CsvUtil.escape(competencyCodes.getOrDefault(competencyId(e), "")))
                    .append(',')
                    .append(e.getCurrentLevel())
                    .append(',')
                    .append(e.getTargetLevel() == null ? "" : e.getTargetLevel())
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(e.getNotes())))
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
                    tenantId, companyId, "COMPETENCY", fileName, startedAt, 0, List.of());
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
                tenantId, companyId, "COMPETENCY", fileName, startedAt, rows.size(), errors);
    }

    private void importRow(UUID tenantId, UUID companyId, List<String> fields) {
        if (fields.size() < 3) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Expected at least 3 columns, found " + fields.size());
        }
        String employeeNumber = requireField(fields.get(0), "employee_number");
        String competencyCode = requireField(fields.get(1), "competency_code");
        int currentLevel = requireInt(fields.get(2), "current_level");
        Integer targetLevel = fields.size() > 3 ? optionalInt(fields.get(3), "target_level") : null;
        String notes = fields.size() > 4 ? CsvUtil.blankToNull(fields.get(4)) : null;

        Employee employee =
                employees
                        .findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                                tenantId, companyId, employeeNumber)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST,
                                                "Unknown employee_number: " + employeeNumber));
        Competency competency =
                competencyRepository
                        .findByTenantIdAndCompanyIdAndCodeIgnoreCase(
                                tenantId, companyId, competencyCode)
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.BAD_REQUEST,
                                                "Unknown competency_code: " + competencyCode));

        employeeCompetencies.upsert(
                new EmployeeCompetencyRequest(
                        tenantId,
                        companyId,
                        employee.getId(),
                        competency.getId(),
                        currentLevel,
                        targetLevel,
                        notes));
    }

    private Map<UUID, String> employeeNumbersFor(List<EmployeeCompetency> rows) {
        List<UUID> ids =
                rows.stream()
                        .map(CompetencyImportExportService::employeeId)
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

    private Map<UUID, String> competencyCodesFor(List<EmployeeCompetency> rows) {
        List<UUID> ids =
                rows.stream()
                        .map(CompetencyImportExportService::competencyId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (Competency c : competencyRepository.findAllById(ids)) {
            out.put(c.getId(), c.getCode());
        }
        return out;
    }

    private static UUID employeeId(EmployeeCompetency e) {
        return e.getEmployee() == null ? null : e.getEmployee().getId();
    }

    private static UUID competencyId(EmployeeCompetency e) {
        return e.getCompetency() == null ? null : e.getCompetency().getId();
    }

    private static String requireField(String value, String fieldName) {
        String v = CsvUtil.blankToNull(value);
        if (v == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return v;
    }

    private static int requireInt(String value, String fieldName) {
        String v = requireField(value, fieldName);
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + v, e);
        }
    }

    private static Integer optionalInt(String value, String fieldName) {
        String v = CsvUtil.blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + v, e);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
