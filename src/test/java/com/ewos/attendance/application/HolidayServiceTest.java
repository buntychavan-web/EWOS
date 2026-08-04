package com.ewos.attendance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.attendance.api.AttendanceMapper;
import com.ewos.attendance.api.dto.CreateHolidayRequest;
import com.ewos.attendance.domain.Holiday;
import com.ewos.attendance.infrastructure.persistence.HolidayRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class HolidayServiceTest {

    @Mock HolidayRepository repository;
    @Mock ClientAccessGuard guard;

    private HolidayService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new HolidayService(repository, new AttendanceMapper(), guard);
    }

    @Test
    void createsATenantWideHolidayWithoutCompanyGuard() {
        CreateHolidayRequest request =
                new CreateHolidayRequest(
                        tenantId, null, LocalDate.of(2026, 1, 26), "Republic Day", true);
        when(repository.findExact(tenantId, null, request.holidayDate()))
                .thenReturn(Optional.empty());
        when(repository.save(any(Holiday.class)))
                .thenAnswer(
                        inv -> {
                            Holiday h = inv.getArgument(0);
                            h.setId(UUID.randomUUID());
                            return h;
                        });

        var response = service.create(request);

        assertThat(response.companyId()).isNull();
        assertThat(response.recurringAnnually()).isTrue();
        verify(guard, org.mockito.Mockito.never()).requireAccessForCompany(any());
    }

    @Test
    void createsACompanyScopedHolidayAndChecksAccess() {
        CreateHolidayRequest request =
                new CreateHolidayRequest(
                        tenantId, companyId, LocalDate.of(2026, 3, 20), "Founders Day", false);
        when(repository.findExact(tenantId, companyId, request.holidayDate()))
                .thenReturn(Optional.empty());
        when(repository.save(any(Holiday.class)))
                .thenAnswer(
                        inv -> {
                            Holiday h = inv.getArgument(0);
                            h.setId(UUID.randomUUID());
                            return h;
                        });

        var response = service.create(request);

        assertThat(response.companyId()).isEqualTo(companyId);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void rejectsADuplicateHolidayForTheSameTenantCompanyAndDate() {
        CreateHolidayRequest request =
                new CreateHolidayRequest(
                        tenantId, companyId, LocalDate.of(2026, 3, 20), "Dup", false);
        when(repository.findExact(tenantId, companyId, request.holidayDate()))
                .thenReturn(Optional.of(new Holiday()));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void deleteThrowsNotFoundForAnUnknownHoliday() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteChecksCompanyAccessWhenHolidayIsCompanyScoped() {
        UUID id = UUID.randomUUID();
        Holiday h = new Holiday();
        h.setId(id);
        h.setTenantId(tenantId);
        h.setCompanyId(companyId);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(h));

        service.delete(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
        verify(repository).delete(h);
    }

    @Test
    void forCompanyListsEffectiveHolidaysAfterAccessCheck() {
        Holiday h = new Holiday();
        h.setId(UUID.randomUUID());
        h.setHolidayDate(LocalDate.of(2026, 1, 26));
        h.setName("Republic Day");
        when(repository.findEffectiveForCompany(tenantId, companyId)).thenReturn(List.of(h));

        List<?> result = service.forCompany(tenantId, companyId);

        assertThat(result).hasSize(1);
        verify(guard).requireAccessForCompany(companyId);
    }
}
