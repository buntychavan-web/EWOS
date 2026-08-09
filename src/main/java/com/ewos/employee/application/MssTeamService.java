package com.ewos.employee.application;

import com.ewos.employee.api.dto.MssOrgUnitRef;
import com.ewos.employee.api.dto.MssTeamMemberDetailResponse;
import com.ewos.employee.api.dto.MssTeamMemberResponse;
import com.ewos.employee.api.dto.MssTeamPageResponse;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmploymentType;
import com.ewos.employee.domain.MssFieldVisibilityConfig;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.organization.domain.OrganizationUnit;
import com.ewos.shared.audit.CrossEmployeeAccessLogService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 27C — My Team direct-report list and single-employee detail (PRD §4.3/§4.4). Direct
 * reports only (PRD's explicit "Option A" scope decision): reads go through {@link
 * EmployeeRepository#findAllByTenantIdAndManagerId} / {@code manager.id} equality, never any
 * hierarchy traversal, so an indirect report is never returned or resolvable by id.
 *
 * <p>Field-level masking calls {@link MssFieldVisibilityService#allForTenant} once per request and
 * builds a local visible-field set, rather than {@code canManagerView} once per field per row — for
 * a page of up to {@value #MAX_PAGE_SIZE} rows and {@value #DETAIL_FIELDS_COUNT} maskable fields
 * that is the difference between one query and hundreds, consistent with the PRD's own &lt;300ms
 * acceptance targets (§9 risk table).
 */
@Service
public class MssTeamService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DETAIL_FIELDS_COUNT = 13;
    private static final String ACTING_FOR_ACTION = "MSS_TEAM_ACTING_FOR";
    private static final String DETAIL_ACTION = "MSS_TEAM_DETAIL";

    private static final List<String> LIST_FIELDS =
            List.of(
                    "employeeNumber",
                    "displayName",
                    "workEmail",
                    "phone",
                    "hireDate",
                    "status",
                    "primaryOrgUnitName",
                    "managerName",
                    "employmentTypeName");

    private static final List<String> DETAIL_ONLY_FIELDS =
            List.of(
                    "dateOfBirth",
                    "personalEmail",
                    "emergencyContactName",
                    "emergencyContactPhone");

    private final EmployeeRepository employees;
    private final EmployeeContext employeeContext;
    private final TenantContext tenantContext;
    private final EffectiveManagerResolver effectiveManagerResolver;
    private final MssFieldVisibilityService fieldVisibility;
    private final CrossEmployeeAccessLogService accessLog;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public MssTeamService(
            EmployeeRepository employees,
            EmployeeContext employeeContext,
            TenantContext tenantContext,
            EffectiveManagerResolver effectiveManagerResolver,
            MssFieldVisibilityService fieldVisibility,
            CrossEmployeeAccessLogService accessLog) {
        this.employees = employees;
        this.employeeContext = employeeContext;
        this.tenantContext = tenantContext;
        this.effectiveManagerResolver = effectiveManagerResolver;
        this.fieldVisibility = fieldVisibility;
        this.accessLog = accessLog;
    }

    @Transactional(readOnly = true)
    public MssTeamPageResponse list(UUID actingForEmployeeId, String cursor, Integer limit) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID callerEmployeeId = requireEmployeeId();
        UUID effectiveManagerId =
                effectiveManagerResolver.resolve(
                        tenantId,
                        callerEmployeeId,
                        actingForEmployeeId,
                        ACTING_FOR_ACTION,
                        "Team not found");

        int pageSize =
                (limit == null || limit <= 0) ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
        CursorKey key = decodeCursor(cursor);
        Pageable window = PageRequest.of(0, pageSize + 1);
        List<Employee> found =
                employees.findDirectReportsAfterCursor(
                        tenantId,
                        effectiveManagerId,
                        key == null ? null : key.displayName(),
                        key == null ? null : key.employeeId(),
                        window);

        boolean hasMore = found.size() > pageSize;
        List<Employee> page = hasMore ? found.subList(0, pageSize) : found;

        Set<String> visible = visibleFieldNames(tenantId);
        List<MssTeamMemberResponse> items = new ArrayList<>();
        for (Employee e : page) {
            items.add(toListItem(e, visible));
        }
        String nextCursor = hasMore ? encodeCursor(page.get(page.size() - 1)) : null;
        return new MssTeamPageResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public MssTeamMemberDetailResponse detail(UUID employeeId, UUID actingForEmployeeId) {
        UUID tenantId = tenantContext.homeTenantId();
        UUID callerEmployeeId = requireEmployeeId();
        UUID effectiveManagerId =
                effectiveManagerResolver.resolve(
                        tenantId,
                        callerEmployeeId,
                        actingForEmployeeId,
                        ACTING_FOR_ACTION,
                        "Team not found");

        Employee employee = employees.findByIdAndTenantId(employeeId, tenantId).orElse(null);
        if (!isDirectReportOf(employee, effectiveManagerId)) {
            accessLog.logDenied(
                    tenantId, callerEmployeeId, employeeId, DETAIL_ACTION, "not a direct report");
            throw new ApiException(HttpStatus.NOT_FOUND, "Team member not found");
        }
        accessLog.logGranted(tenantId, callerEmployeeId, employeeId, DETAIL_ACTION);

        Set<String> visible = visibleFieldNames(tenantId);
        return toDetail(employee, visible);
    }

    private static boolean isDirectReportOf(Employee employee, UUID effectiveManagerId) {
        return employee != null
                && employee.getManager() != null
                && effectiveManagerId.equals(employee.getManager().getId());
    }

    private UUID requireEmployeeId() {
        return employeeContext
                .currentEmployeeId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "No employee record is linked to your account"));
    }

    private Set<String> visibleFieldNames(UUID tenantId) {
        Set<String> visible = new HashSet<>();
        fieldVisibility.allForTenant(tenantId).stream()
                .filter(MssFieldVisibilityConfig::isManagerCanView)
                .forEach(c -> visible.add(c.getFieldName()));
        return visible;
    }

    private static MssTeamMemberResponse toListItem(Employee e, Set<String> visible) {
        return new MssTeamMemberResponse(
                e.getId(),
                visible.contains("displayName") ? displayNameOf(e) : null,
                visible.contains("employeeNumber") ? e.getEmployeeNumber() : null,
                visible.contains("workEmail") ? e.getWorkEmail() : null,
                visible.contains("phone") ? e.getPhone() : null,
                visible.contains("hireDate") ? e.getHireDate() : null,
                visible.contains("status") ? e.getStatus() : null,
                visible.contains("primaryOrgUnitName") ? orgUnitRef(e.getPrimaryOrgUnit()) : null,
                visible.contains("managerName") ? displayNameOf(e.getManager()) : null,
                visible.contains("employmentTypeName") ? employmentTypeNameOf(e) : null,
                maskedFields(visible, LIST_FIELDS));
    }

    private static MssTeamMemberDetailResponse toDetail(Employee e, Set<String> visible) {
        List<String> allFields = new ArrayList<>(LIST_FIELDS);
        allFields.addAll(DETAIL_ONLY_FIELDS);
        return new MssTeamMemberDetailResponse(
                e.getId(),
                visible.contains("displayName") ? displayNameOf(e) : null,
                visible.contains("employeeNumber") ? e.getEmployeeNumber() : null,
                visible.contains("workEmail") ? e.getWorkEmail() : null,
                visible.contains("phone") ? e.getPhone() : null,
                visible.contains("dateOfBirth") ? e.getDateOfBirth() : null,
                visible.contains("hireDate") ? e.getHireDate() : null,
                visible.contains("status") ? e.getStatus() : null,
                visible.contains("primaryOrgUnitName") ? orgUnitRef(e.getPrimaryOrgUnit()) : null,
                visible.contains("managerName") ? displayNameOf(e.getManager()) : null,
                visible.contains("employmentTypeName") ? employmentTypeNameOf(e) : null,
                visible.contains("personalEmail") ? e.getPersonalEmail() : null,
                visible.contains("emergencyContactName") ? e.getEmergencyContactName() : null,
                visible.contains("emergencyContactPhone") ? e.getEmergencyContactPhone() : null,
                maskedFields(visible, allFields));
    }

    private static List<String> maskedFields(Set<String> visible, List<String> allFields) {
        List<String> masked = new ArrayList<>();
        for (String field : allFields) {
            if (!visible.contains(field)) {
                masked.add(field);
            }
        }
        return masked;
    }

    private static MssOrgUnitRef orgUnitRef(OrganizationUnit unit) {
        return unit == null ? null : new MssOrgUnitRef(unit.getId(), unit.getName());
    }

    private static String employmentTypeNameOf(Employee e) {
        EmploymentType type = e.getEmploymentType();
        return type == null ? null : type.getName();
    }

    private static String displayNameOf(Employee e) {
        if (e == null) {
            return null;
        }
        if (e.getDisplayName() != null && !e.getDisplayName().isBlank()) {
            return e.getDisplayName();
        }
        String first = e.getFirstName() == null ? "" : e.getFirstName();
        String last = e.getLastName() == null ? "" : e.getLastName();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    /**
     * Sprint 27C fix-round (F3) — PRD §4.3's exact {@code Base64(displayName|employeeId)} keyset
     * cursor. {@code employeeId} (a UUID, never containing {@code |}) is always the last field, so
     * decoding splits on the <em>last</em> {@code |} rather than the first — correct even for the
     * unlikely case of a display name that itself contains a {@code |}.
     */
    private static CursorKey decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int lastPipe = raw.lastIndexOf('|');
            if (lastPipe < 0) {
                throw new IllegalArgumentException("missing displayName|employeeId separator");
            }
            String displayName = raw.substring(0, lastPipe);
            UUID employeeId = UUID.fromString(raw.substring(lastPipe + 1));
            return new CursorKey(displayName, employeeId);
        } catch (RuntimeException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid cursor", e);
        }
    }

    private static String encodeCursor(Employee e) {
        String displayName = e.getDisplayName() == null ? "" : e.getDisplayName();
        String raw = displayName + "|" + e.getId();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record CursorKey(String displayName, UUID employeeId) {}
}
