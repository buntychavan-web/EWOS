package com.ewos.goals.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.goals.api.dto.CreateGoalRequest;
import com.ewos.goals.domain.Goal;
import com.ewos.goals.domain.GoalPriority;
import com.ewos.goals.domain.GoalScope;
import com.ewos.goals.domain.GoalType;
import com.ewos.goals.infrastructure.persistence.GoalRepository;
import com.ewos.importexport.api.dto.ImportResultResponse;
import com.ewos.importexport.api.dto.ImportRowError;
import com.ewos.importexport.application.ImportJobRecorder;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.io.CsvUtil;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 24E — CSV export/template/bulk-import for Goals. Every valid row is created through the
 * existing, unmodified {@link GoalService#create}, so import rows go through exactly the same
 * validation and event-publishing (goal-assigned notifications, etc.) a single admin-tier create
 * call would. This class is deliberately not {@code @Transactional} itself — each row's {@code
 * create()} call commits or rolls back independently (Spring's normal per-method transaction
 * boundary), which is what makes partial success possible: one bad row never rolls back the rows
 * before or after it.
 */
@Service
public class GoalImportExportService {

    private static final String HEADER =
            "employee_number,code,name,description,goal_type,scope,period_start,period_end,"
                    + "weightage,target,unit_of_measure,priority,status,progress_percent";

    private static final String TEMPLATE =
            HEADER
                    + "\nE0001,SALES-Q1,Grow enterprise pipeline,Close 5 new logos,KPI,INDIVIDUAL,"
                    + "2026-01-01,2026-03-31,40,5,accounts,HIGH,,";

    private final GoalService goals;
    private final GoalRepository goalRepository;
    private final EmployeeRepository employees;
    private final ImportJobRecorder jobs;

    public GoalImportExportService(
            GoalService goals,
            GoalRepository goalRepository,
            EmployeeRepository employees,
            ImportJobRecorder jobs) {
        this.goals = goals;
        this.goalRepository = goalRepository;
        this.employees = employees;
        this.jobs = jobs;
    }

    public String templateCsv() {
        return TEMPLATE;
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID tenantId, UUID companyId) {
        List<Goal> all = goalRepository.findAllByTenantIdAndCompanyId(tenantId, companyId);
        Map<UUID, String> employeeNumbers = employeeNumbersFor(all);

        StringBuilder sb = new StringBuilder(256 + all.size() * 128);
        sb.append(HEADER).append('\n');
        for (Goal g : all) {
            sb.append(CsvUtil.escape(employeeNumbers.getOrDefault(employeeId(g), "")))
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(g.getCode())))
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(g.getName())))
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(g.getDescription())))
                    .append(',')
                    .append(g.getGoalType())
                    .append(',')
                    .append(g.getScope())
                    .append(',')
                    .append(dateOrEmpty(g.getPeriodStart()))
                    .append(',')
                    .append(dateOrEmpty(g.getPeriodEnd()))
                    .append(',')
                    .append(g.getWeightage() == null ? "" : g.getWeightage().toPlainString())
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(g.getTarget())))
                    .append(',')
                    .append(CsvUtil.escape(nullSafe(g.getUnitOfMeasure())))
                    .append(',')
                    .append(g.getPriority() == null ? "" : g.getPriority())
                    .append(',')
                    .append(g.getStatus())
                    .append(',')
                    .append(
                            g.getProgressPercent() == null
                                    ? ""
                                    : g.getProgressPercent().toPlainString())
                    .append('\n');
        }
        return sb.toString();
    }

    public ImportResultResponse importCsv(
            UUID tenantId, UUID companyId, String fileName, String csvBody) {
        Instant startedAt = Instant.now();
        List<String> lines = CsvUtil.splitLines(csvBody);
        if (lines.isEmpty()) {
            return jobs.record(tenantId, companyId, "GOAL", fileName, startedAt, 0, List.of());
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

        return jobs.record(tenantId, companyId, "GOAL", fileName, startedAt, rows.size(), errors);
    }

    private void importRow(UUID tenantId, UUID companyId, List<String> fields) {
        if (fields.size() < 12) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "Expected at least 12 columns, found " + fields.size());
        }
        String employeeNumber = CsvUtil.blankToNull(fields.get(0));
        String code = requireField(fields.get(1), "code");
        String name = requireField(fields.get(2), "name");
        String description = CsvUtil.blankToNull(fields.get(3));
        GoalType goalType = requireEnum(fields.get(4), GoalType.class, "goal_type");
        GoalScope scope = requireEnum(fields.get(5), GoalScope.class, "scope");
        LocalDate periodStart = requireDate(fields.get(6), "period_start");
        LocalDate periodEnd = requireDate(fields.get(7), "period_end");
        BigDecimal weightage = optionalDecimal(fields.get(8), "weightage");
        String target = CsvUtil.blankToNull(fields.get(9));
        String unitOfMeasure = CsvUtil.blankToNull(fields.get(10));
        GoalPriority priority =
                CsvUtil.blankToNull(fields.get(11)) == null
                        ? null
                        : requireEnum(fields.get(11), GoalPriority.class, "priority");

        UUID employeeId = null;
        if (employeeNumber != null) {
            Employee employee =
                    employees
                            .findByTenantIdAndCompanyIdAndEmployeeNumberIgnoreCase(
                                    tenantId, companyId, employeeNumber)
                            .orElseThrow(
                                    () ->
                                            new ApiException(
                                                    HttpStatus.BAD_REQUEST,
                                                    "Unknown employee_number: " + employeeNumber));
            employeeId = employee.getId();
        }

        goals.create(
                new CreateGoalRequest(
                        tenantId,
                        companyId,
                        null,
                        null,
                        code,
                        name,
                        description,
                        goalType,
                        scope,
                        employeeId,
                        null,
                        null,
                        periodStart,
                        periodEnd,
                        weightage,
                        target,
                        unitOfMeasure,
                        priority));
    }

    private Map<UUID, String> employeeNumbersFor(List<Goal> goalsList) {
        List<UUID> ids =
                goalsList.stream()
                        .map(GoalImportExportService::employeeId)
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

    private static UUID employeeId(Goal g) {
        return g.getEmployee() == null ? null : g.getEmployee().getId();
    }

    private static String requireField(String value, String fieldName) {
        String v = CsvUtil.blankToNull(value);
        if (v == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return v;
    }

    private static <E extends Enum<E>> E requireEnum(
            String value, Class<E> type, String fieldName) {
        String v = requireField(value, fieldName);
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + v, e);
        }
    }

    private static LocalDate requireDate(String value, String fieldName) {
        String v = requireField(value, fieldName);
        try {
            return LocalDate.parse(v.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid " + fieldName + " (expected YYYY-MM-DD): " + v,
                    e);
        }
    }

    private static BigDecimal optionalDecimal(String value, String fieldName) {
        String v = CsvUtil.blankToNull(value);
        if (v == null) {
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid " + fieldName + ": " + v, e);
        }
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }

    private static String dateOrEmpty(LocalDate d) {
        return d == null ? "" : d.toString();
    }
}
