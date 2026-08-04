package com.ewos.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.CreateAttendancePolicyRequest;
import com.ewos.attendance.api.dto.UpdateAttendancePolicyRequest;
import com.ewos.attendance.domain.AttendancePolicy;
import com.ewos.attendance.infrastructure.persistence.AttendancePolicyRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Attendance policy CRUD: code uniqueness per tenant, the nullable-companyId case (a tenant-wide
 * policy skips the company guard since there is no single company to check), and the
 * company-scoped-wins-over-tenant-wide effective-policy resolution used by {@link TimesheetService}
 * when opening a period.
 */
@ExtendWith(MockitoExtension.class)
class AttendancePolicyServiceTest {

    @Mock AttendancePolicyRepository repository;
    @Mock ClientAccessGuard guard;

    private AttendancePolicyService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AttendancePolicyService(repository, new AttendanceMapper(), guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(AttendancePolicy.class)))
                .thenAnswer(
                        inv -> {
                            AttendancePolicy p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId(UUID.randomUUID());
                            }
                            return p;
                        });
    }

    private CreateAttendancePolicyRequest request(UUID company, String code) {
        return new CreateAttendancePolicyRequest(
                tenantId,
                company,
                code,
                "Standard",
                null,
                new BigDecimal("8.00"),
                new BigDecimal("40.00"),
                "MON,TUE,WED,THU,FRI",
                10,
                new BigDecimal("1.50"),
                30,
                true,
                null);
    }

    @Test
    void createRejectsADuplicateCodeForTheSameTenant() {
        when(repository.existsByTenantIdAndCodeIgnoreCase(tenantId, "STD")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(companyId, "STD")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createChecksCompanyAccessWhenCompanyScoped() {
        when(repository.existsByTenantIdAndCodeIgnoreCase(tenantId, "STD")).thenReturn(false);

        service.create(request(companyId, "STD"));

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createSkipsTheCompanyGuardForATenantWidePolicy() {
        when(repository.existsByTenantIdAndCodeIgnoreCase(tenantId, "STD")).thenReturn(false);

        service.create(request(null, "STD"));

        verify(guard, never()).requireAccessForCompany(any());
    }

    @Test
    void updateOnlyOverwritesSuppliedFields() {
        UUID id = UUID.randomUUID();
        AttendancePolicy p = new AttendancePolicy();
        p.setId(id);
        p.setCompanyId(companyId);
        p.setName("Standard");
        p.setGraceMinutes(10);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));

        UpdateAttendancePolicyRequest req =
                new UpdateAttendancePolicyRequest(
                        null, null, null, null, null, 15, null, null, null, null);
        var response = service.update(tenantId, id, req);

        assertThat(response.name()).isEqualTo("Standard");
        assertThat(response.graceMinutes()).isEqualTo(15);
    }

    @Test
    void deleteThrowsNotFoundForAnUnknownPolicy() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listFiltersOutNullCompanyIdsBeforeCheckingAccess() {
        AttendancePolicy tenantWide = new AttendancePolicy();
        tenantWide.setCompanyId(null);
        AttendancePolicy companyScoped = new AttendancePolicy();
        companyScoped.setCompanyId(companyId);
        when(repository.findAllByTenantIdOrderByNameAsc(tenantId))
                .thenReturn(List.of(tenantWide, companyScoped));

        service.list(tenantId);

        verify(guard).requireAccessForCompanies(List.of(companyId));
    }

    @Test
    void effectivePolicyForThrowsConflictWhenNoActivePolicyExists() {
        when(repository.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.effectivePolicyFor(tenantId, companyId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void effectivePolicyForReturnsTheFirstCandidateFromTheRepositoryQuery() {
        // The repository query is expected to order company-scoped before tenant-wide; the service
        // trusts that ordering and takes the first result rather than re-sorting.
        AttendancePolicy companyScoped = new AttendancePolicy();
        companyScoped.setId(UUID.randomUUID());
        AttendancePolicy tenantWide = new AttendancePolicy();
        tenantWide.setId(UUID.randomUUID());
        when(repository.findEffectiveForCompany(tenantId, companyId))
                .thenReturn(List.of(companyScoped, tenantWide));

        AttendancePolicy resolved = service.effectivePolicyFor(tenantId, companyId);

        assertThat(resolved.getId()).isEqualTo(companyScoped.getId());
    }
}
