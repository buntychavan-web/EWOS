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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
        Employee e = new Employee();
        e.setId(id);
        e.setEmployeeNumber("E100");
        e.setFirstName("Jane");
        e.setLastName("Doe");
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
        Employee report = directReport(reportId, managerEmployeeId);
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(managerEmployeeId), any()))
                .thenReturn(new PageImpl<>(List.of(report)));
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

    @Test
    void listPaginatesByDisplayNameAscendingAndEncodesNextCursorWhenMoreRowsExist() {
        Employee report = directReport(UUID.randomUUID(), managerEmployeeId);
        Page<Employee> hasNextPage = new PageImpl<>(List.of(report), PageRequest.of(0, 1), 5);
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(managerEmployeeId), any()))
                .thenReturn(hasNextPage);
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        MssTeamPageResponse page = service.list(null, null, 1);

        assertThat(page.nextCursor()).isNotBlank();
        var pageableCaptor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(employees)
                .findAllByTenantIdAndManagerId(
                        eq(tenantId), eq(managerEmployeeId), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("displayName")).isNotNull();
    }

    @Test
    void listReturnsNoNextCursorOnTheLastPage() {
        Employee report = directReport(UUID.randomUUID(), managerEmployeeId);
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(managerEmployeeId), any()))
                .thenReturn(new PageImpl<>(List.of(report)));
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
    void listDelegatesThroughEffectiveManagerResolverForActingFor() {
        UUID peerId = UUID.randomUUID();
        UUID peerManagerId = UUID.randomUUID();
        when(effectiveManagerResolver.resolve(
                        eq(tenantId), eq(managerEmployeeId), eq(peerId), anyString(), anyString()))
                .thenReturn(peerManagerId);
        when(employees.findAllByTenantIdAndManagerId(eq(tenantId), eq(peerManagerId), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(fieldVisibility.allForTenant(tenantId)).thenReturn(List.of());

        service.list(peerId, null, null);

        verify(employees).findAllByTenantIdAndManagerId(eq(tenantId), eq(peerManagerId), any());
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
        verify(employees, never()).findAllByTenantIdAndManagerId(any(), any(), any());
    }
}
