package com.ewos.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.recruitment.api.RecruitmentMapper;
import com.ewos.recruitment.api.dto.CreateJobPositionRequest;
import com.ewos.recruitment.api.dto.UpdateJobPositionRequest;
import com.ewos.recruitment.domain.EmploymentType;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.events.RecruitmentEvent;
import com.ewos.recruitment.infrastructure.persistence.JobPositionRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class JobPositionServiceTest {

    @Mock JobPositionRepository positions;
    @Mock OrganizationUnitRepository orgUnits;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final RecruitmentMapper mapper = new RecruitmentMapper();

    private JobPositionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new JobPositionService(positions, orgUnits, mapper, events, guard);
    }

    private CreateJobPositionRequest createRequest(BigDecimal min, BigDecimal max) {
        return new CreateJobPositionRequest(
                tenantId,
                companyId,
                "ENG-1",
                "Engineer",
                null,
                null,
                null,
                EmploymentType.FULL_TIME,
                "L3",
                "USD",
                min,
                max,
                null);
    }

    private JobPosition position(boolean active) {
        JobPosition p = new JobPosition();
        p.setId(UUID.randomUUID());
        p.setTenantId(tenantId);
        p.setCompanyId(companyId);
        p.setActive(active);
        return p;
    }

    @Test
    void createRejectsDuplicateCode() {
        when(positions.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "ENG-1"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(null, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(positions, never()).save(any());
    }

    @Test
    void createRejectsInvertedSalaryBand() {
        when(positions.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "ENG-1"))
                .thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        createRequest(
                                                new BigDecimal("100000"), new BigDecimal("50000"))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(positions, never()).save(any());
    }

    @Test
    void createSucceedsAndDefaultsActiveTrue() {
        when(positions.existsByTenantIdAndCompanyIdAndCodeIgnoreCase(tenantId, companyId, "ENG-1"))
                .thenReturn(false);
        when(positions.save(any(JobPosition.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.create(createRequest(new BigDecimal("50000"), new BigDecimal("100000")));

        assertThat(resp.active()).isTrue();
        verify(guard).requireAccessForCompany(companyId);
        verify(events).publishEvent(any(RecruitmentEvent.class));
    }

    @Test
    void updateRejectsInvertedSalaryBand() {
        JobPosition p = position(true);
        when(positions.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));

        var req =
                new UpdateJobPositionRequest(
                        "Engineer",
                        null,
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        "L3",
                        "USD",
                        new BigDecimal("100000"),
                        new BigDecimal("50000"),
                        null);

        assertThatThrownBy(() -> service.update(tenantId, p.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updatePublishesActivatedEventOnlyWhenActiveFlagFlips() {
        JobPosition p = position(true);
        when(positions.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));

        var req =
                new UpdateJobPositionRequest(
                        "Engineer",
                        null,
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        "L3",
                        "USD",
                        null,
                        null,
                        false);

        service.update(tenantId, p.getId(), req);

        assertThat(p.isActive()).isFalse();
        verify(events, times(2)).publishEvent(any(RecruitmentEvent.class));
    }

    @Test
    void deleteChecksCompanyAccessBeforeDeleting() {
        JobPosition p = position(true);
        when(positions.findByIdAndTenantId(p.getId(), tenantId)).thenReturn(Optional.of(p));

        service.delete(tenantId, p.getId());

        verify(guard).requireAccessForCompany(companyId);
        verify(positions).delete(p);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(positions.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
