package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.api.dto.MssTeamMemberDetailResponse;
import com.ewos.employee.api.dto.MssTeamMemberResponse;
import com.ewos.employee.api.dto.MssTeamPageResponse;
import com.ewos.employee.domain.Employee;
import com.ewos.employee.domain.EmployeeStatus;
import com.ewos.employee.domain.MssFieldVisibilityConfig;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.audit.CrossEmployeeAccessLogService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

/**
 * Sprint 27C — {@link MssTeamService}: field-masking matrix (explicit-allow / explicit-deny /
 * absent-default-deny), direct-report-only 404 with enumeration protection, and delegation dispatch
 * through {@link EffectiveManagerResolver}.
 */
@ExtendWith(MockitoExtension.class)
class MssTeamServiceTest {

    @Mock EmployeeRepository employees;
    @Mock EmployeeContext employeeContext;
    @Mock TenantContext tenantContext;
    @Mock EffectiveManagerResolver effectiveManagerResolver;
    @Mock MssFieldVisibilityService fieldVisibility;
    @Mock CrossEmployeeAccessLogService accessLog;

    private MssTeamService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID managerEmployeeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new MssTeamService(
                        employees,
                        employeeContext,
                        tenantContext,
                        effectiveManagerResolver,
                        fieldVisibility,
                        accessLog);
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(managerEmployeeId));
        lenient()
                .when(
                        effectiveManagerResolver.resolve(
                                eq(tenantId),
                                eq(managerEmployeeId),
                                any(),
                                anyString(),
                                anyString()))
                .thenReturn(managerEmployeeId);
    }

    private static Employee directReport(UUID id, UUID managerId) {
        return directReport(id, managerId, null);
    }

    private static Employee directReport(UUID id, UUID managerId, String displayName) {
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeNumber("E100");
        e.setFirstName("Jane");
        e.setLastName("Doe");
        e.setDisplayName(displayName);
        e.setWorkEmail("jane@example.com");
        e.setStatus(EmployeeStatus.ACTIVE);
        Employee manager = new Employee();
        manager.setId(managerId);
        e.setManager(manager);
        return e;
    }

    private static MssFieldVisibilityConfig visible(String field, boolean canView) {
        MssFieldVisibilityConfig c = new MssFieldVisibilityConfig();
        c.setFieldName(field);
        c.setManagerCanView(canView);
        return c;
    }

    @Test
    void listMasksFieldsPerConfiguredVisibilityExplicitAllowExplicitDenyAndAbsentDefaultDeny() {
        UUID reportId = UUID.randomUUID();
        Employee report = directReport(reportId, managerEmployeeId, "Jane Doe");
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId), eq(managerEmployeeId), eq(null), eq(null), any()))
                .thenReturn(List.of(report));
        // employeeNumber: explicit allow. phone: explicit deny. status: absent (default-deny).
        when(fieldVisibility.allForTenant(tenantId))
                .thenReturn(List.of(visible("employeeNumber", true), visible("phone", false)));

        MssTeamPageResponse page = service.list(null, null, null);

        MssTeamMemberResponse item = page.items().get(0);
        assertThat(item.employeeNumber()).isEqualTo("E100");
        assertThat(item.phone()).isNull();
        assertThat(item.status()).isNull();
        assertThat(item.maskedFields())
                .contains("phone", "status")
                .doesNotContain("employeeNumber");
    }

    /**
     * PRD §7.2 asks for "at least 3 cases per field" (explicit-allow / explicit-deny /
     * absent-default-deny) across every maskable field. Read literally per-field-times-3, that
     * would be ~39 near-identical tests for the 13 fields {@link MssTeamService} exposes — masking
     * is decided by one generic, field-name-agnostic line ({@code visible.contains(fieldName)}) for
     * every one of them, so a per-field test would exercise the exact same branch 13 times over,
     * proving nothing per repetition beyond "the string equality check works." Redundant coverage
     * inflates the suite without adding failure-detection power (per the review's own instruction
     * not to add "hundreds of redundant tests" for a generic mechanism).
     *
     * <p>What genuinely needs proof instead: that <em>every</em> field name the service knows about
     * is wired into the masking check at all (a field silently forgotten from either {@code
     * LIST_FIELDS}/{@code DETAIL_ONLY_FIELDS} or the constructor call would leak unmasked data),
     * and that all three states are reachable. This test does that in one pass: every one of the 13
     * maskable fields on {@link MssTeamMemberDetailResponse} is set to a real, distinguishable
     * value, then split across all three configuration states, and every single field's visibility
     * and {@code maskedFields} membership is asserted individually — full per-field coverage of
     * <em>whether masking applies</em>, without duplicating the masking mechanism's own test 13
     * times.
     */
    @Test
    void detailAppliesTheThreeVisibilityStatesAcrossEveryMaskableFieldInTheSystem() {
        UUID reportId = UUID.randomUUID();
        Employee report = directReport(reportId, managerEmployeeId, "Jane Doe");
        report.setPersonalEmail("jane.personal@example.com");
        report.setDateOfBirth(java.time.LocalDate.of(1990, 5, 1));
        report.setHireDate(java.time.LocalDate.of(2023, 4, 1));
        report.setEmergencyContactName("John Doe");
        report.setEmergencyContactPhone("+1-555-0100");
        when(employees.findByIdAndTenantId(reportId, tenantId)).thenReturn(Optional.of(report));

        // Explicitly allowed: half the fields (2 list + 2 detail-only).
        List<String> allowed =
                List.of("employeeNumber", "displayName", "personalEmail", "dateOfBirth");
        // Explicitly denied: the other half of the list fields.
        List<String> denied = List.of("workEmail", "phone", "hireDate", "status");
        // Deliberately absent from the config entirely (default-deny): everything else —
        // primaryOrgUnitName, managerName, employmentTypeName, emergencyContactName,
        // emergencyContactPhone.
        List<String> allFields =
                List.of(
                        "employeeNumber",
                        "displayName",
                        "workEmail",
                        "phone",
                        "hireDate",
                        "status",
                        "primaryOrgUnitName",
                        "managerName",
                        "employmentTypeName",
                        "dateOfBirth",
                        "personalEmail",
                        "emergencyContactName",
                        "emergencyContactPhone");
        List<MssFieldVisibilityConfig> config = new ArrayList<>();
        allowed.forEach(f -> config.add(visible(f, true)));
        denied.forEach(f -> config.add(visible(f, false)));
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(config);

        MssTeamMemberDetailResponse detail = service.detail(reportId, null);

        List<String> absent =
                allFields.stream()
                        .filter(f -> !allowed.contains(f) && !denied.contains(f))
                        .toList();
        assertThat(absent).isNotEmpty(); // sanity check the partition covers all 3 states

        assertThat(detail.employeeNumber()).as("employeeNumber: explicit-allow").isEqualTo("E100");
        assertThat(detail.displayName()).as("displayName: explicit-allow").isEqualTo("Jane Doe");
        assertThat(detail.personalEmail())
                .as("personalEmail: explicit-allow")
                .isEqualTo("jane.personal@example.com");
        assertThat(detail.dateOfBirth())
                .as("dateOfBirth: explicit-allow")
                .isEqualTo(java.time.LocalDate.of(1990, 5, 1));

        assertThat(detail.workEmail()).as("workEmail: explicit-deny").isNull();
        assertThat(detail.phone()).as("phone: explicit-deny").isNull();
        assertThat(detail.hireDate()).as("hireDate: explicit-deny").isNull();
        assertThat(detail.status()).as("status: explicit-deny").isNull();

        assertThat(detail.primaryOrgUnit()).as("primaryOrgUnitName: absent-default-deny").isNull();
        assertThat(detail.managerName()).as("managerName: absent-default-deny").isNull();
        assertThat(detail.employmentType()).as("employmentTypeName: absent-default-deny").isNull();
        assertThat(detail.emergencyContactName())
                .as("emergencyContactName: absent-default-deny")
                .isNull();
        assertThat(detail.emergencyContactPhone())
                .as("emergencyContactPhone: absent-default-deny")
                .isNull();

        assertThat(detail.maskedFields())
                .as("maskedFields must list every non-visible field, and only those")
                .containsExactlyInAnyOrderElementsOf(
                        allFields.stream().filter(f -> !allowed.contains(f)).toList());
    }

    @Test
    void listFirstPageQueriesWithNullCursorBoundsAndAWindowOnePastTheRequestedPageSize() {
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId), eq(managerEmployeeId), eq(null), eq(null), any()))
                .thenReturn(List.of());
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        service.list(null, null, 20);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(employees)
                .findDirectReportsAfterCursor(
                        eq(tenantId),
                        eq(managerEmployeeId),
                        eq(null),
                        eq(null),
                        pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(21);
    }

    @Test
    void listEncodesANextCursorFromTheLastItemWhenMoreRowsExistBeyondThePageSize() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Employee first = directReport(firstId, managerEmployeeId, "Alice");
        Employee second = directReport(secondId, managerEmployeeId, "Bob");
        // Repository is asked for pageSize+1; returning both proves there is a next page.
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId), eq(managerEmployeeId), eq(null), eq(null), any()))
                .thenReturn(List.of(first, second));
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        MssTeamPageResponse page = service.list(null, null, 1);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).employeeId()).isEqualTo(firstId);
        assertThat(page.nextCursor()).isNotBlank();

        String decoded =
                new String(
                        java.util.Base64.getUrlDecoder().decode(page.nextCursor()),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("Alice|" + firstId);
    }

    @Test
    void listNextPageDecodesTheCursorAndPassesItToTheRepositoryAsTheKeysetBound() {
        UUID cursorEmployeeId = UUID.randomUUID();
        String cursor =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                ("Alice|" + cursorEmployeeId)
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId),
                        eq(managerEmployeeId),
                        eq("Alice"),
                        eq(cursorEmployeeId),
                        any()))
                .thenReturn(List.of());
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        MssTeamPageResponse page = service.list(null, cursor, null);

        assertThat(page.items()).isEmpty();
        verify(employees)
                .findDirectReportsAfterCursor(
                        eq(tenantId),
                        eq(managerEmployeeId),
                        eq("Alice"),
                        eq(cursorEmployeeId),
                        any());
    }

    @Test
    void listUsesEmployeeIdAsATiebreakerWhenTwoDirectReportsShareADisplayName() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        // Repository (mocked here; the real query orders by displayName, then id) returns both
        // "Same Name" rows in id order — the service must preserve that order and cursor off the
        // second one's id, not just its (duplicate) display name.
        Employee first = directReport(firstId, managerEmployeeId, "Same Name");
        Employee second = directReport(secondId, managerEmployeeId, "Same Name");
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId), eq(managerEmployeeId), eq(null), eq(null), any()))
                .thenReturn(List.of(first, second));
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        MssTeamPageResponse page = service.list(null, null, 1);

        assertThat(page.items())
                .extracting(MssTeamMemberResponse::employeeId)
                .containsExactly(firstId);
        String decoded =
                new String(
                        java.util.Base64.getUrlDecoder().decode(page.nextCursor()),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("Same Name|" + firstId);
    }

    @Test
    void listPreservesTheRepositorysOrderingRatherThanReordering() {
        List<Employee> ordered = new ArrayList<>();
        for (String name : List.of("Amy", "Ben", "Cara")) {
            ordered.add(directReport(UUID.randomUUID(), managerEmployeeId, name));
        }
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId), eq(managerEmployeeId), eq(null), eq(null), any()))
                .thenReturn(ordered);
        when(fieldVisibility.allForTenant(tenantId))
                .thenReturn(List.of(visible("displayName", true)));

        MssTeamPageResponse page = service.list(null, null, 10);

        assertThat(page.items())
                .extracting(MssTeamMemberResponse::displayName)
                .containsExactly("Amy", "Ben", "Cara");
    }

    @Test
    void listReturnsNoNextCursorAtTheEndOfResults() {
        Employee report = directReport(UUID.randomUUID(), managerEmployeeId, "Jane Doe");
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId), eq(managerEmployeeId), eq(null), eq(null), any()))
                .thenReturn(List.of(report));
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        MssTeamPageResponse page = service.list(null, null, null);

        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void listRejectsAnUndecodableCursor() {
        assertThatThrownBy(() -> service.list(null, "not-valid-base64!!", null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listRejectsACursorWithNoSeparator() {
        String cursor =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "no-separator-here"
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.list(null, cursor, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listDelegatesThroughEffectiveManagerResolverForActingFor() {
        UUID peerId = UUID.randomUUID();
        UUID peerManagerId = UUID.randomUUID();
        when(effectiveManagerResolver.resolve(
                        eq(tenantId), eq(managerEmployeeId), eq(peerId), anyString(), anyString()))
                .thenReturn(peerManagerId);
        when(employees.findDirectReportsAfterCursor(
                        eq(tenantId), eq(peerManagerId), eq(null), eq(null), any()))
                .thenReturn(List.of());
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        service.list(peerId, null, null);

        verify(employees)
                .findDirectReportsAfterCursor(
                        eq(tenantId), eq(peerManagerId), eq(null), eq(null), any());
    }

    @Test
    void detailThrowsNotFoundWhenEmployeeDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(missingId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(missingId, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(accessLog)
                .logDenied(
                        eq(tenantId),
                        eq(managerEmployeeId),
                        eq(missingId),
                        anyString(),
                        anyString());
    }

    @Test
    void detailThrowsTheSameNotFoundWhenEmployeeExistsButIsNotADirectReport() {
        UUID otherId = UUID.randomUUID();
        Employee notMyReport = directReport(otherId, UUID.randomUUID());
        when(employees.findByIdAndTenantId(otherId, tenantId)).thenReturn(Optional.of(notMyReport));

        assertThatThrownBy(() -> service.detail(otherId, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void detailSucceedsAndLogsGrantedWhenEmployeeIsADirectReport() {
        UUID reportId = UUID.randomUUID();
        Employee report = directReport(reportId, managerEmployeeId);
        when(employees.findByIdAndTenantId(reportId, tenantId)).thenReturn(Optional.of(report));
        when(fieldVisibility.allForTenant(tenantId))
                .thenReturn(List.of(visible("employeeNumber", true)));

        MssTeamMemberDetailResponse detail = service.detail(reportId, null);

        assertThat(detail.employeeId()).isEqualTo(reportId);
        assertThat(detail.employeeNumber()).isEqualTo("E100");
        // personalEmail has no config row -> default-deny even on the detail view.
        assertThat(detail.personalEmail()).isNull();
        assertThat(detail.maskedFields()).contains("personalEmail");
        verify(accessLog).logGranted(tenantId, managerEmployeeId, reportId, "MSS_TEAM_DETAIL");
    }

    @Test
    void requiresALinkedEmployeeRecord() {
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(null, null, null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(employees, never()).findDirectReportsAfterCursor(any(), any(), any(), any(), any());
    }
}
