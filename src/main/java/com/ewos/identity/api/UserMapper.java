package com.ewos.identity.api;

import com.ewos.identity.api.dto.RoleSummary;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Maps identity aggregate roots into API response records. Kept explicit and reflection-free so the
 * shape of an {@link UserResponse} is greppable and the mapping stays fast on hot paths.
 */
@Component
public final class UserMapper {

    public UserResponse toResponse(User user) {
        Set<RoleSummary> roles =
                Optional.ofNullable(user.getRoles()).orElse(Set.of()).stream()
                        .map(this::toRoleSummary)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEnabled(),
                user.isAccountNonLocked(),
                roles,
                user.getLastLoginAt(),
                user.getPasswordChangedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getCreatedBy(),
                user.getUpdatedBy());
    }

    public RoleSummary toRoleSummary(Role role) {
        return new RoleSummary(role.getId(), role.getName());
    }
}
