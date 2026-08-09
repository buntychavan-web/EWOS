package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.shared.audit.CrossEmployeeAccessLogService;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.TenantContext;
import com.ewos.workflow.application.WorkflowDelegationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Sprint 27B/27C — extracted from {@code ManagerApprovalsServiceTest} when {@code
 * ManagerApprovalsService}'s private {@code resolveEffectiveManagerEmployeeId} became this shared
 * {@link EffectiveManagerResolver}, reused by {@code MssTeamService}/{@code MssDashboardService}.
 */
@ExtendWith(MockitoExtension.class)
class EffectiveManagerResolverTest {

    @Mock EmployeeRepository employees;
    @Mock TenantContext tenantContext;
    @Mock WorkflowDelegationService delegations;
    @Mock CrossEmployeeAccessLogService accessLog;

    private EffectiveManagerResolver resolver;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID callerEmployeeId = UUID.randomUUID();
    private final UUID callerUserId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resolver = new EffectiveManagerResolver(employees, tenantContext, delegations, accessLog);
    }

    @Test
    void returnsCallerEmployeeIdWhenActingForEmployeeIdIsNull() {
        UUID result = resolver.resolve(tenantId, callerEmployeeId, null);

        assertThat(result).isEqualTo(callerEmployeeId);
    }

    @Test
    void returnsCallerEmployeeIdWhenActingForEmployeeIdEqualsCaller() {
        UUID result = resolver.resolve(tenantId, callerEmployeeId, callerEmployeeId);

        assertThat(result).isEqualTo(callerEmployeeId);
    }

    @Test
    void throwsNotFoundAndLogsDeniedWhenPeerHasNoActiveDelegationToCaller() {
        UUID peerEmployeeId = UUID.randomUUID();
        Employee peer = new Employee();
        peer.setId(peerEmployeeId);
        peer.setUserId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(peerEmployeeId, tenantId)).thenReturn(Optional.of(peer));
        when(tenantContext.currentUserId()).thenReturn(Optional.of(callerUserId));
        when(delegations.isActiveDelegateOf(tenantId, peer.getUserId(), callerUserId))
                .thenReturn(false);

        assertThatThrownBy(() -> resolver.resolve(tenantId, callerEmployeeId, peerEmployeeId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(accessLog)
                .logDenied(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(callerEmployeeId),
                        org.mockito.ArgumentMatchers.eq(peerEmployeeId),
                        any(),
                        any());
    }

    @Test
    void throwsNotFoundWhenActingForEmployeeIdDoesNotExistInTenant() {
        UUID unknownId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(unknownId, tenantId)).thenReturn(Optional.empty());
        when(tenantContext.currentUserId()).thenReturn(Optional.of(callerUserId));

        assertThatThrownBy(() -> resolver.resolve(tenantId, callerEmployeeId, unknownId))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returnsActingForEmployeeIdAndLogsGrantedWhenActiveDelegationExists() {
        UUID peerEmployeeId = UUID.randomUUID();
        Employee peer = new Employee();
        peer.setId(peerEmployeeId);
        peer.setUserId(UUID.randomUUID());
        when(employees.findByIdAndTenantId(peerEmployeeId, tenantId)).thenReturn(Optional.of(peer));
        when(tenantContext.currentUserId()).thenReturn(Optional.of(callerUserId));
        when(delegations.isActiveDelegateOf(tenantId, peer.getUserId(), callerUserId))
                .thenReturn(true);

        UUID result = resolver.resolve(tenantId, callerEmployeeId, peerEmployeeId);

        assertThat(result).isEqualTo(peerEmployeeId);
        verify(accessLog)
                .logGranted(
                        org.mockito.ArgumentMatchers.eq(tenantId),
                        org.mockito.ArgumentMatchers.eq(callerEmployeeId),
                        org.mockito.ArgumentMatchers.eq(peerEmployeeId),
                        any());
    }

    @Test
    void usesTheSuppliedActionAndMessageOverload() {
        UUID peerEmployeeId = UUID.randomUUID();
        when(employees.findByIdAndTenantId(peerEmployeeId, tenantId)).thenReturn(Optional.empty());
        when(tenantContext.currentUserId()).thenReturn(Optional.of(callerUserId));

        assertThatThrownBy(
                        () ->
                                resolver.resolve(
                                        tenantId,
                                        callerEmployeeId,
                                        peerEmployeeId,
                                        "MSS_TEAM_ACTING_FOR",
                                        "Team not found"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Team not found");
    }
}
