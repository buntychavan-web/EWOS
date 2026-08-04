package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.api.TenancyMapper;
import com.ewos.tenancy.api.dto.CreateTenantAccessGrantRequest;
import com.ewos.tenancy.api.dto.TenantAccessGrantResponse;
import com.ewos.tenancy.domain.TenantAccessGrant;
import com.ewos.tenancy.infrastructure.persistence.TenantAccessGrantRepository;
import com.ewos.tenancy.infrastructure.persistence.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class TenantAccessGrantServiceTest {

    @Mock TenantAccessGrantRepository repository;
    @Mock TenantRepository tenantRepository;
    @Mock TenantContext tenantContext;
    @Mock ApplicationEventPublisher events;

    private TenantAccessGrantService service;

    @BeforeEach
    void setUp() {
        service =
                new TenantAccessGrantService(
                        repository, tenantRepository, tenantContext, new TenancyMapper(), events);
    }

    @Test
    void grantSavesAScopedAuditedGrant() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID grantedBy = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(1, ChronoUnit.DAYS);
        when(tenantRepository.existsById(tenantId)).thenReturn(true);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(grantedBy));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TenantAccessGrantResponse response =
                service.grant(
                        new CreateTenantAccessGrantRequest(userId, tenantId, "reason", expiresAt));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.tenantId()).isEqualTo(tenantId);
        assertThat(response.reason()).isEqualTo("reason");
        assertThat(response.expiresAt()).isEqualTo(expiresAt);
        assertThat(response.active()).isTrue();

        org.mockito.ArgumentCaptor<TenantAccessGrant> captor =
                org.mockito.ArgumentCaptor.forClass(TenantAccessGrant.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGrantedBy()).isEqualTo(grantedBy);

        org.mockito.ArgumentCaptor<com.ewos.tenancy.domain.events.TenancyEvent> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        com.ewos.tenancy.domain.events.TenancyEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(com.ewos.tenancy.domain.events.TenancyEventType.ACCESS_GRANTED);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(userId);
        assertThat(eventCaptor.getValue().grantedTenantId()).isEqualTo(tenantId);
    }

    @Test
    void grantRejectsUnknownTenant() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.existsById(tenantId)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.grant(
                                        new CreateTenantAccessGrantRequest(
                                                UUID.randomUUID(),
                                                tenantId,
                                                "reason",
                                                Instant.now().plus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ApiException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void revokeMarksGrantRevoked() {
        UUID id = UUID.randomUUID();
        UUID revokedBy = UUID.randomUUID();
        TenantAccessGrant grant = activeGrant();
        when(repository.findById(id)).thenReturn(Optional.of(grant));
        when(tenantContext.currentUserId()).thenReturn(Optional.of(revokedBy));

        TenantAccessGrantResponse response = service.revoke(id);

        assertThat(response.revokedAt()).isNotNull();
        assertThat(response.revokedBy()).isEqualTo(revokedBy);
        assertThat(response.active()).isFalse();

        org.mockito.ArgumentCaptor<com.ewos.tenancy.domain.events.TenancyEvent> eventCaptor =
                org.mockito.ArgumentCaptor.forClass(
                        com.ewos.tenancy.domain.events.TenancyEvent.class);
        verify(events).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType())
                .isEqualTo(com.ewos.tenancy.domain.events.TenancyEventType.ACCESS_REVOKED);
        assertThat(eventCaptor.getValue().userId()).isEqualTo(grant.getUserId());
    }

    @Test
    void revokeRejectsAlreadyRevokedGrant() {
        UUID id = UUID.randomUUID();
        TenantAccessGrant grant = activeGrant();
        grant.setRevokedAt(Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(grant));

        assertThatThrownBy(() -> service.revoke(id)).isInstanceOf(ApiException.class);
    }

    @Test
    void revokeRejectsUnknownGrant() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(id)).isInstanceOf(ApiException.class);
    }

    @Test
    void listForUserDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        when(repository.findAllByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(activeGrant()));

        assertThat(service.listForUser(userId)).hasSize(1);
    }

    @Test
    void hasActiveGrantDelegatesToRepository() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(repository.existsByUserIdAndTenantIdAndRevokedAtIsNullAndExpiresAtAfter(
                        eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(true);

        assertThat(service.hasActiveGrant(userId, tenantId)).isTrue();
    }

    private static TenantAccessGrant activeGrant() {
        TenantAccessGrant grant = new TenantAccessGrant();
        grant.setUserId(UUID.randomUUID());
        grant.setTenantId(UUID.randomUUID());
        grant.setGrantedBy(UUID.randomUUID());
        grant.setReason("reason");
        grant.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        return grant;
    }
}
