package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.tenancy.domain.UserTenantMembership;
import com.ewos.tenancy.infrastructure.persistence.UserTenantMembershipRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Sprint 1.4 audit remediation, Finding 2. */
@ExtendWith(MockitoExtension.class)
class BootstrapTenantMembershipProvisionerTest {

    private static final UUID BOOTSTRAP_TENANT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock UserTenantMembershipRepository memberships;

    private BootstrapTenantMembershipProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new BootstrapTenantMembershipProvisioner(memberships);
    }

    @Test
    void createsMembershipWhenUserHasNone() {
        UUID userId = UUID.randomUUID();
        when(memberships.findByUserId(userId)).thenReturn(Optional.empty());

        provisioner.ensureDefaultMembership(userId);

        ArgumentCaptor<UserTenantMembership> captor =
                ArgumentCaptor.forClass(UserTenantMembership.class);
        verify(memberships).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getTenantId()).isEqualTo(BOOTSTRAP_TENANT_ID);
        assertThat(captor.getValue().isPrimary()).isTrue();
    }

    @Test
    void isNoOpWhenUserAlreadyHasAMembership() {
        UUID userId = UUID.randomUUID();
        when(memberships.findByUserId(userId)).thenReturn(Optional.of(new UserTenantMembership()));

        provisioner.ensureDefaultMembership(userId);

        verify(memberships, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
