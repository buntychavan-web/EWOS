package com.ewos.workflow.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.workflow.api.dto.CreateDelegationRequest;
import com.ewos.workflow.domain.WorkflowDelegation;
import com.ewos.workflow.infrastructure.persistence.WorkflowDelegationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class WorkflowDelegationServiceTest {

    @Mock WorkflowDelegationRepository delegations;

    private WorkflowDelegationService service;
    private UUID caller;

    @BeforeEach
    void setUp() {
        service = new WorkflowDelegationService(delegations);
        caller = UUID.randomUUID();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(caller.toString(), null, List.of()));
        lenient().when(delegations.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createRejectsDelegatingToSelf() {
        CreateDelegationRequest request =
                new CreateDelegationRequest(caller, null, Instant.now(), Instant.now().plusSeconds(3600), null);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsEndBeforeStart() {
        Instant now = Instant.now();
        CreateDelegationRequest request =
                new CreateDelegationRequest(UUID.randomUUID(), null, now, now.minusSeconds(60), null);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSavesADelegationFromTheCurrentCaller() {
        UUID tenantId = UUID.randomUUID();
        UUID delegate = UUID.randomUUID();
        Instant start = Instant.now();
        Instant end = start.plus(7, ChronoUnit.DAYS);

        service.create(tenantId, new CreateDelegationRequest(delegate, "APPROVER", start, end, "OOO"));

        var captor = org.mockito.ArgumentCaptor.forClass(WorkflowDelegation.class);
        org.mockito.Mockito.verify(delegations).save(captor.capture());
        WorkflowDelegation saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getDelegatorActorId()).isEqualTo(caller);
        assertThat(saved.getDelegateActorId()).isEqualTo(delegate);
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void revokeRejectsWhenDelegationBelongsToSomeoneElse() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        WorkflowDelegation d = new WorkflowDelegation();
        d.setDelegatorActorId(UUID.randomUUID());
        when(delegations.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.revoke(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void revokeDeactivatesOwnDelegation() {
        UUID tenantId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        WorkflowDelegation d = new WorkflowDelegation();
        d.setDelegatorActorId(caller);
        d.setActive(true);
        when(delegations.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.of(d));

        service.revoke(tenantId, id);

        assertThat(d.isActive()).isFalse();
    }

    @Test
    void isActiveDelegateOfTrueWhenAnActiveDelegationMatches() {
        UUID tenantId = UUID.randomUUID();
        UUID delegator = UUID.randomUUID();
        WorkflowDelegation d = new WorkflowDelegation();
        d.setDelegateActorId(caller);
        when(delegations.findActiveFor(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(delegator), any()))
                .thenReturn(List.of(d));

        assertThat(service.isActiveDelegateOf(tenantId, delegator, caller)).isTrue();
    }

    @Test
    void isActiveDelegateOfFalseWhenNoneMatch() {
        UUID tenantId = UUID.randomUUID();
        UUID delegator = UUID.randomUUID();
        when(delegations.findActiveFor(org.mockito.ArgumentMatchers.eq(tenantId), org.mockito.ArgumentMatchers.eq(delegator), any()))
                .thenReturn(List.of());

        assertThat(service.isActiveDelegateOf(tenantId, delegator, caller)).isFalse();
    }
}
