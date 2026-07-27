package com.ewos.identity.application;

import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures the default System Administrator user exists on every boot. Idempotent — does nothing if
 * the user is already present.
 *
 * <p>Also ensures that user has a tenant membership (Sprint 1.4 audit remediation, Finding 2):
 * {@code V38__tenant_resolution.sql}'s backfill runs once, at migration time, over whatever is
 * already in {@code users} — on a genuinely fresh deployment that's nobody, since this bootstrap
 * runs <em>after</em> Flyway migrations complete. Without the {@link
 * DefaultTenantMembershipProvisioner} call below, the bootstrap admin could never resolve a tenant
 * at all. Runs on every boot, not just first-creation, so a deployment that predates this fix
 * self-heals on its next restart — the provisioner itself is a no-op once a membership exists.
 */
@Component
public class IdentityBootstrap implements ApplicationRunner {

    static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(IdentityBootstrap.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties properties;
    private final DefaultTenantMembershipProvisioner tenantMembershipProvisioner;

    public IdentityBootstrap(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            BootstrapProperties properties,
            DefaultTenantMembershipProvisioner tenantMembershipProvisioner) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.tenantMembershipProvisioner = tenantMembershipProvisioner;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Optional<User> existing = userRepository.findByUsername(properties.username());
        if (existing.isPresent()) {
            log.debug(
                    "Default admin '{}' already present — skipping creation.",
                    properties.username());
            tenantMembershipProvisioner.ensureDefaultMembership(existing.get().getId());
            return;
        }

        Role admin =
                roleRepository
                        .findByName(SYSTEM_ADMIN_ROLE)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Missing SYSTEM_ADMIN role — Flyway migrations may not have completed."));

        User user = new User();
        user.setUsername(properties.username());
        user.setEmail(properties.email());
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setPasswordChangedAt(Instant.now());
        user.getRoles().add(admin);
        User saved = userRepository.save(user);
        tenantMembershipProvisioner.ensureDefaultMembership(saved.getId());

        log.warn(
                "Bootstrapped default System Administrator '{}'. Change the password immediately.",
                properties.username());
    }
}
