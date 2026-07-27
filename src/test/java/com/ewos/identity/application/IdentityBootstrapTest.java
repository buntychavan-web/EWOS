package com.ewos.identity.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Sprint 1.4 audit remediation, Finding 2. Verifies {@link DefaultTenantMembershipProvisioner} is
 * consulted on every boot — both for a freshly-created admin and for one that already existed (so a
 * deployment that predates this fix self-heals on its next restart).
 */
@ExtendWith(MockitoExtension.class)
class IdentityBootstrapTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock DefaultTenantMembershipProvisioner tenantMembershipProvisioner;
    @Mock ApplicationArguments args;

    private final BootstrapProperties properties =
            new BootstrapProperties("admin", "admin@ewos.local", "T3mp0rary!Pass");

    private IdentityBootstrap bootstrap;

    @BeforeEach
    void setUp() {
        bootstrap =
                new IdentityBootstrap(
                        userRepository,
                        roleRepository,
                        passwordEncoder,
                        properties,
                        tenantMembershipProvisioner);
    }

    @Test
    void provisionsTenantMembershipForNewlyCreatedAdmin() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        Role systemAdminRole = new Role("SYSTEM_ADMIN", "System administrator");
        systemAdminRole.setId(UUID.randomUUID());
        when(roleRepository.findByName("SYSTEM_ADMIN")).thenReturn(Optional.of(systemAdminRole));
        when(passwordEncoder.encode(any())).thenReturn("hash");
        UUID generatedId = UUID.randomUUID();
        when(userRepository.save(any(User.class)))
                .thenAnswer(
                        inv -> {
                            User u = inv.getArgument(0);
                            u.setId(generatedId);
                            return u;
                        });

        bootstrap.run(args);

        verify(tenantMembershipProvisioner).ensureDefaultMembership(generatedId);
    }

    @Test
    void provisionsTenantMembershipForAlreadyExistingAdmin() {
        // The self-healing case: a deployment created before this fix existed still gets its
        // bootstrap
        // admin's membership backfilled on the next restart, not just at first-ever creation.
        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        bootstrap.run(args);

        verify(tenantMembershipProvisioner).ensureDefaultMembership(existing.getId());
        verify(userRepository, never()).save(any());
    }

    @Test
    void doesNotCreateASecondAdminWhenOneAlreadyExists() {
        User existing = new User();
        existing.setId(UUID.randomUUID());
        existing.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existing));

        bootstrap.run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, never()).save(captor.capture());
    }
}
