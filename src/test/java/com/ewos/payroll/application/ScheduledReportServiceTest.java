package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.dto.CreateScheduledReportRequest;
import com.ewos.payroll.domain.ScheduledReport;
import com.ewos.payroll.domain.ScheduledReportFormat;
import com.ewos.payroll.infrastructure.persistence.ScheduledReportRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Scheduled-report declarations: default format when omitted, the tri-state {@code active} flag
 * (null leaves the entity default, explicit true/false is honoured), and access-checked
 * deactivate/delete/list.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledReportServiceTest {

    @Mock ScheduledReportRepository repository;
    @Mock ClientAccessGuard guard;

    private ScheduledReportService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ScheduledReportService(repository, guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(ScheduledReport.class)))
                .thenAnswer(
                        inv -> {
                            ScheduledReport s = inv.getArgument(0);
                            if (s.getId() == null) {
                                s.setId(UUID.randomUUID());
                            }
                            return s;
                        });
    }

    private CreateScheduledReportRequest request(ScheduledReportFormat format, Boolean active) {
        return new CreateScheduledReportRequest(
                tenantId,
                companyId,
                "SALARY_REG",
                "Monthly salary register",
                "0 0 1 * *",
                format,
                Map.of("period", "monthly"),
                "payroll@example.com",
                active);
    }

    @Test
    void createChecksCompanyAccess() {
        service.create(request(ScheduledReportFormat.CSV, null));

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createDefaultsFormatToCsvWhenOmitted() {
        var response = service.create(request(null, null));

        assertThat(response.format()).isEqualTo(ScheduledReportFormat.CSV);
    }

    @Test
    void createHonorsAnExplicitFormat() {
        var response = service.create(request(ScheduledReportFormat.PDF, null));

        assertThat(response.format()).isEqualTo(ScheduledReportFormat.PDF);
    }

    @Test
    void createHonorsAnExplicitFalseActiveFlag() {
        var response = service.create(request(ScheduledReportFormat.CSV, false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void createLeavesTheDefaultActiveTrueWhenFlagIsOmitted() {
        var response = service.create(request(ScheduledReportFormat.CSV, null));

        assertThat(response.active()).isTrue();
    }

    @Test
    void deactivateThrowsNotFoundForAnUnknownReport() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deactivateChecksAccessAndFlipsTheActiveFlag() {
        UUID id = UUID.randomUUID();
        ScheduledReport s = new ScheduledReport();
        s.setId(id);
        s.setCompanyId(companyId);
        s.setActive(true);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(s));

        service.deactivate(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
        assertThat(s.isActive()).isFalse();
    }

    @Test
    void deleteChecksAccessBeforeDeleting() {
        UUID id = UUID.randomUUID();
        ScheduledReport s = new ScheduledReport();
        s.setId(id);
        s.setCompanyId(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(s));

        service.delete(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
        verify(repository).delete(s);
    }

    @Test
    void deleteThrowsNotFoundForAnUnknownReportAndNeverDeletes() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, id)).isInstanceOf(ApiException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void listChecksCompanyAccess() {
        when(repository.findAllByTenantIdAndCompanyIdOrderByNameAsc(tenantId, companyId))
                .thenReturn(List.of());

        service.list(tenantId, companyId);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownReport() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
