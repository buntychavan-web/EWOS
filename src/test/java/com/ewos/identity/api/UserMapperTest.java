package com.ewos.identity.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserMapperTest {

    private final UserMapper mapper = new UserMapper();

    @Test
    void mapsAllFieldsIncludingRoles() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        Role role = new Role();
        role.setId(roleId);
        role.setName("SYSTEM_ADMIN");

        LinkedHashSet<Role> roles = new LinkedHashSet<>();
        roles.add(role);

        Instant now = Instant.now();
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setEnabled(true);
        user.setAccountNonLocked(true);
        user.setRoles(roles);
        user.setLastLoginAt(now);
        user.setPasswordChangedAt(now.minusSeconds(60));
        // createdAt / updatedAt / createdBy / updatedBy are populated by Spring's
        // AuditingEntityListener at persist/update time — they stay null in this bare unit test.

        UserResponse response = mapper.toResponse(user);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.enabled()).isTrue();
        assertThat(response.accountNonLocked()).isTrue();
        assertThat(response.roles()).extracting("id").containsExactly(roleId);
        assertThat(response.roles()).extracting("name").containsExactly("SYSTEM_ADMIN");
        assertThat(response.lastLoginAt()).isEqualTo(user.getLastLoginAt());
        assertThat(response.passwordChangedAt()).isEqualTo(user.getPasswordChangedAt());
        assertThat(response.createdAt()).isNull();
        assertThat(response.updatedAt()).isNull();
        assertThat(response.createdBy()).isNull();
        assertThat(response.updatedBy()).isNull();
    }

    @Test
    void nullRolesCollectionMapsToEmpty() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail("bob@example.com");
        user.setEnabled(false);
        user.setAccountNonLocked(false);
        user.setRoles(null);

        UserResponse response = mapper.toResponse(user);

        assertThat(response.roles()).isEmpty();
    }
}
