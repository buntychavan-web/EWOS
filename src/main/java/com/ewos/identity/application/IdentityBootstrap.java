package com.ewos.identity.application;

import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Ensures the default System Administrator user exists on every boot.
 * Idempotent — does nothing if the user is already present.
 */
@Component
public class IdentityBootstrap implements ApplicationRunner {

    static final String SYSTEM_ADMIN_ROLE = "SYSTEM_ADMIN";

    private static final Logger log = LoggerFactory.getLogger(IdentityBootstrap.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final BootstrapProperties properties;

    public IdentityBootstrap(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder,
                             BootstrapProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(properties.username())) {
            log.debug("Default admin '{}' already present — skipping bootstrap.", properties.username());
            return;
        }

        Role admin = roleRepository.findByName(SYSTEM_ADMIN_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Missing SYSTEM_ADMIN role — Flyway migrations may not have completed."));

        User user = new User();
        user.setUsername(properties.username());
        user.setEmail(properties.email());
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setPasswordChangedAt(Instant.now());
        user.getRoles().add(admin);
        userRepository.save(user);

        log.warn("Bootstrapped default System Administrator '{}'. Change the password immediately.",
                properties.username());
    }
}
