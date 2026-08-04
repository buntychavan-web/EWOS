package com.ewos.payroll.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.payroll.api.dto.PayrollRunReopenAuthorizationResponse;
import com.ewos.payroll.api.dto.ReopenPayrollRunRequest;
import com.ewos.payroll.domain.PayrollRun;
import com.ewos.payroll.domain.PayrollRunReopenAuthorization;
import com.ewos.payroll.domain.PayrollRunReopenAuthorizationStatus;
import com.ewos.payroll.domain.PayrollRunStatus;
import com.ewos.payroll.infrastructure.persistence.PayrollRunReopenAuthorizationRepository;
import com.ewos.payroll.infrastructure.persistence.PayrollRunRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Sprint 24L item 2 — the FROZEN-run side of the reopen/correction framework. */
@ExtendWith(MockitoExtension.class)
class PayrollReopenServiceTest {

    @Mock PayrollRunReopenAuthorizationRepository authorizations;
    @Mock PayrollRunRepository runs;
    @Mock ClientAccessGuard guard;
    @Mock ApplicationEventPublisher events;

    private PayrollReopenService service;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID runId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PayrollReopenService(authorizations, runs, guard, events);
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                UUID.randomUUID().toString(), "n/a", List.of()));
        org.mockito.Mockito.lenient()
                .when(authorizations.save(any(PayrollRunReopenAuthorization.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private PayrollRun run(PayrollRunStatus status) {
        PayrollRun r = new PayrollRun();
        r.setId(runId);
        r.setCompanyId(companyId);
        r.setStatus(status);
        return r;
    }

    @Test
    void authorizeReopenRejectsARunThatIsNotFrozen() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FINALIZED)));

        assertThatThrownBy(
                        () ->
                                service.authorizeReopen(
                                        tenantId, runId, new ReopenPayrollRunRequest("reason")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void authorizeReopenRejectsWhenAnActiveAuthorizationAlreadyExists() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FROZEN)));
        when(authorizations.findActiveForRun(tenantId, runId))
                .thenReturn(Optional.of(new PayrollRunReopenAuthorization()));

        assertThatThrownBy(
                        () ->
                                service.authorizeReopen(
                                        tenantId, runId, new ReopenPayrollRunRequest("reason")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void authorizeReopenCreatesAnActiveAuthorizationForAFrozenRun() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FROZEN)));
        when(authorizations.findActiveForRun(tenantId, runId)).thenReturn(Optional.empty());

        PayrollRunReopenAuthorizationResponse response =
                service.authorizeReopen(
                        tenantId, runId, new ReopenPayrollRunRequest("missed the arrear window"));

        assertThat(response.status()).isEqualTo(PayrollRunReopenAuthorizationStatus.ACTIVE);
        assertThat(response.reason()).isEqualTo("missed the arrear window");
        assertThat(response.payrollRunId()).isEqualTo(runId);
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void revokeRejectsAnAuthorizationThatIsNotActive() {
        PayrollRunReopenAuthorization authorization = new PayrollRunReopenAuthorization();
        authorization.setId(UUID.randomUUID());
        authorization.setTenantId(tenantId);
        authorization.setCompanyId(companyId);
        authorization.setPayrollRun(run(PayrollRunStatus.FROZEN));
        authorization.setStatus(PayrollRunReopenAuthorizationStatus.CONSUMED);
        when(authorizations.findById(authorization.getId())).thenReturn(Optional.of(authorization));

        assertThatThrownBy(() -> service.revoke(tenantId, authorization.getId()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void revokeMarksAnActiveAuthorizationAsRevoked() {
        PayrollRunReopenAuthorization authorization = new PayrollRunReopenAuthorization();
        authorization.setId(UUID.randomUUID());
        authorization.setTenantId(tenantId);
        authorization.setCompanyId(companyId);
        authorization.setPayrollRun(run(PayrollRunStatus.FROZEN));
        authorization.setStatus(PayrollRunReopenAuthorizationStatus.ACTIVE);
        when(authorizations.findById(authorization.getId())).thenReturn(Optional.of(authorization));

        PayrollRunReopenAuthorizationResponse response =
                service.revoke(tenantId, authorization.getId());

        assertThat(response.status()).isEqualTo(PayrollRunReopenAuthorizationStatus.REVOKED);
        assertThat(authorization.getRevokedAt()).isNotNull();
        assertThat(authorization.getRevokedBy()).isNotNull();
    }

    @Test
    void historyForRunReturnsEveryAuthorizationOrderedByAuthorizedAt() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FROZEN)));
        PayrollRunReopenAuthorization a1 = new PayrollRunReopenAuthorization();
        a1.setPayrollRun(run(PayrollRunStatus.FROZEN));
        a1.setStatus(PayrollRunReopenAuthorizationStatus.REVOKED);
        when(authorizations.findAllForRun(tenantId, runId)).thenReturn(List.of(a1));

        List<PayrollRunReopenAuthorizationResponse> history =
                service.historyForRun(tenantId, runId);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).status()).isEqualTo(PayrollRunReopenAuthorizationStatus.REVOKED);
    }

    @Test
    void authorizeReopenPublishesARunReopenAuthorizedEvent() {
        when(runs.findByIdAndTenantId(runId, tenantId))
                .thenReturn(Optional.of(run(PayrollRunStatus.FROZEN)));
        when(authorizations.findActiveForRun(tenantId, runId)).thenReturn(Optional.empty());

        service.authorizeReopen(tenantId, runId, new ReopenPayrollRunRequest("reason"));

        ArgumentCaptor<com.ewos.payroll.domain.events.PayrollEvent> captor =
                ArgumentCaptor.forClass(com.ewos.payroll.domain.events.PayrollEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().eventType())
                .isEqualTo(com.ewos.payroll.domain.events.PayrollEventType.RUN_REOPEN_AUTHORIZED);
    }
}
