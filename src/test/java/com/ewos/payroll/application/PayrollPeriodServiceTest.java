package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.PayrollMapper;
import com.ewos.payroll.api.dto.CreatePayrollPeriodRequest;
import com.ewos.payroll.domain.PayrollFrequency;
import com.ewos.payroll.domain.PayrollPeriod;
import com.ewos.payroll.domain.PayrollPeriodStatus;
import com.ewos.payroll.domain.PayrollPolicy;
import com.ewos.payroll.infrastructure.persistence.PayrollPeriodRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Sprint 14.2 — covers the "tenant-wide list with no companyId parameter at all" pattern: {@link
 * PayrollPeriodService#byStatus} guards every distinct company id present in the result set rather
 * than a single upfront id.
 */
@ExtendWith(MockitoExtension.class)
class PayrollPeriodServiceTest {

    @Mock PayrollPeriodRepository repository;
    @Mock PayrollRunRepository runs;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private PayrollPeriodService service;

    @BeforeEach
    void setUp() {
        service =
                new PayrollPeriodService(
                        repository, runs, new PayrollPolicy(), new PayrollMapper(), events, guard);
        org.mockito.Mockito.lenient()
                .when(repository.save(any(PayrollPeriod.class)))
                .thenAnswer(
                        inv -> {
                            PayrollPeriod p = inv.getArgument(0);
                            if (p.getId() == null) {
                                p.setId(UUID.randomUUID());
                            }
                            return p;
                        });
    }

    private static PayrollPeriod period(UUID companyId, PayrollPeriodStatus status) {
        PayrollPeriod p = new PayrollPeriod();
        p.setId(UUID.randomUUID());
        p.setCompanyId(companyId);
        p.setPeriodStart(LocalDate.of(2026, 1, 1));
        p.setPeriodEnd(LocalDate.of(2026, 1, 31));
        p.setStatus(status);
        return p;
    }

    @Test
    void closeSucceedsWhenEveryRunAgainstThePeriodIsTerminal() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollPeriod p = period(companyId, PayrollPeriodStatus.LOCKED);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));
        when(runs.existsNonTerminalRunForPeriod(tenantId, id)).thenReturn(false);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));

        var response = service.close(tenantId, id);

        assertThat(response.status()).isEqualTo(PayrollPeriodStatus.CLOSED);
        SecurityContextHolder.clearContext();
    }

    @Test
    void closeRefusesWhenAnyRunAgainstThePeriodIsNotYetFinalizedOrFrozen() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollPeriod p = period(companyId, PayrollPeriodStatus.LOCKED);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));
        when(runs.existsNonTerminalRunForPeriod(tenantId, id)).thenReturn(true);

        assertThatThrownBy(() -> service.close(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(p.getStatus()).isEqualTo(PayrollPeriodStatus.LOCKED);
    }

    @Test
    void createChecksAccessForTheRequestedCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        service.create(
                new CreatePayrollPeriodRequest(
                        tenantId,
                        companyId,
                        "JAN-2026",
                        "January 2026",
                        PayrollFrequency.MONTHLY,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 2, 1)));

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void createDeniedWhenCallerLacksCompanyAccess() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompany(companyId);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePayrollPeriodRequest(
                                                tenantId,
                                                companyId,
                                                "JAN-2026",
                                                "January 2026",
                                                PayrollFrequency.MONTHLY,
                                                LocalDate.of(2026, 1, 1),
                                                LocalDate.of(2026, 1, 31),
                                                LocalDate.of(2026, 2, 1))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void getByIdChecksAccessForThePeriodsCompany() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        PayrollPeriod p = period(companyId, PayrollPeriodStatus.OPEN);
        when(repository.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(p));

        service.getById(tenantId, id);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void forCompanyChecksAccessBeforeQuerying() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findAllForCompany(tenantId, companyId)).thenReturn(List.of());

        service.forCompany(tenantId, companyId);

        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void byStatusChecksAccessForEveryDistinctCompanyAcrossTheTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID companyA = UUID.randomUUID();
        UUID companyB = UUID.randomUUID();
        when(repository.findAllByTenantIdAndStatusOrderByPeriodStartDesc(
                        tenantId, PayrollPeriodStatus.OPEN))
                .thenReturn(
                        List.of(
                                period(companyA, PayrollPeriodStatus.OPEN),
                                period(companyB, PayrollPeriodStatus.OPEN)));

        List<?> results = service.byStatus(tenantId, PayrollPeriodStatus.OPEN);

        assertThat(results).hasSize(2);
        verify(guard).requireAccessForCompanies(List.of(companyA, companyB));
    }

    @Test
    void byStatusDeniedWhenAnyResultCompanyIsUnauthorized() {
        UUID tenantId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        when(repository.findAllByTenantIdAndStatusOrderByPeriodStartDesc(
                        tenantId, PayrollPeriodStatus.OPEN))
                .thenReturn(List.of(period(companyId, PayrollPeriodStatus.OPEN)));
        doThrow(new ApiException(HttpStatus.FORBIDDEN, "Not authorized"))
                .when(guard)
                .requireAccessForCompanies(List.of(companyId));

        assertThatThrownBy(() -> service.byStatus(tenantId, PayrollPeriodStatus.OPEN))
                .isInstanceOf(ApiException.class);
    }
}
