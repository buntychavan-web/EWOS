package com.ewos.identity.application;

import com.ewos.common.exception.ApiException;
import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.RoleSummary;
import com.ewos.identity.api.dto.UpdateUserRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.api.dto.UserSearchCriteria;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.identity.infrastructure.persistence.UserSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordHistoryService passwordHistory;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicyValidator passwordPolicy,
                       PasswordHistoryService passwordHistory) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.passwordHistory = passwordHistory;
    }

    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(HttpStatus.CONFLICT, "Username already in use");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already in use");
        }
        passwordPolicy.validate(request.password());

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setAccountNonLocked(true);
        user.setPasswordChangedAt(Instant.now());
        user.setRoles(resolveRoles(request.roleIds()));

        User saved = userRepository.save(user);
        passwordHistory.record(saved, saved.getPasswordHash());
        return toResponse(saved);
    }

    public UserResponse update(UUID id, UpdateUserRequest request) {
        User user = requireUser(id);

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new ApiException(HttpStatus.CONFLICT, "Email already in use");
            }
            user.setEmail(request.email());
        }
        if (request.roleIds() != null) {
            user.setRoles(resolveRoles(request.roleIds()));
        }
        return toResponse(user);
    }

    public UserResponse setEnabled(UUID id, boolean enabled) {
        User user = requireUser(id);
        user.setEnabled(enabled);
        return toResponse(user);
    }

    public void resetPassword(UUID id, String newPassword) {
        User user = requireUser(id);
        applyNewPassword(user, newPassword);
    }

    public void changePassword(UUID actorId, String currentPassword, String newPassword) {
        User user = requireUser(actorId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Current password is incorrect");
        }
        applyNewPassword(user, newPassword);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        return toResponse(requireUser(id));
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(UserSearchCriteria criteria, Pageable pageable) {
        return userRepository.findAll(UserSpecifications.matching(criteria), pageable)
                .map(this::toResponse);
    }

    private void applyNewPassword(User user, String newRawPassword) {
        passwordPolicy.validate(newRawPassword);
        passwordHistory.assertNotReused(user, newRawPassword);
        String hash = passwordEncoder.encode(newRawPassword);
        user.setPasswordHash(hash);
        user.setPasswordChangedAt(Instant.now());
        passwordHistory.record(user, hash);
    }

    private Set<Role> resolveRoles(Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        if (roles.size() != roleIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "One or more role IDs are unknown");
        }
        return roles;
    }

    private User requireUser(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private UserResponse toResponse(User user) {
        Set<RoleSummary> roleSummaries = Optional.ofNullable(user.getRoles())
                .orElse(Set.of())
                .stream()
                .map(role -> new RoleSummary(role.getId(), role.getName()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.isAccountNonLocked(),
                roleSummaries,
                user.getLastLoginAt(),
                user.getPasswordChangedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getCreatedBy(),
                user.getUpdatedBy()
        );
    }
}
