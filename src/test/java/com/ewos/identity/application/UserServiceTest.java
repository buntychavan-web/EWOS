package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.UpdateUserRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.shared.exception.ApiException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordPolicyValidator passwordPolicy;
    @Mock PasswordHistoryService passwordHistory;

    private UserService service;

    @BeforeEach
    void setUp() {
        service =
                new UserService(
                        userRepository,
                        roleRepository,
                        passwordEncoder,
                        passwordPolicy,
                        passwordHistory,
                        new com.ewos.identity.api.UserMapper());
        lenient()
                .when(userRepository.save(any(User.class)))
                .thenAnswer(
                        inv -> {
                            User u = inv.getArgument(0);
                            if (u.getId() == null) u.setId(UUID.randomUUID());
                            return u;
                        });
        lenient()
                .when(passwordEncoder.encode(anyString()))
                .thenAnswer(inv -> "hash(" + inv.getArgument(0) + ")");
    }

    @Test
    void createEncodesPasswordAndRecordsHistory() {
        Role role = role("USER");
        when(roleRepository.findAllById(Set.of(role.getId()))).thenReturn(List.of(role));

        UserResponse response =
                service.create(
                        new CreateUserRequest(
                                "jane",
                                "jane@ewos.local",
                                "Str0ng!Pass",
                                Set.of(role.getId()),
                                null));

        verify(passwordPolicy).validate("Str0ng!Pass");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getUsername()).isEqualTo("jane");
        assertThat(saved.getEmail()).isEqualTo("jane@ewos.local");
        assertThat(saved.getPasswordHash()).isEqualTo("hash(Str0ng!Pass)");
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPasswordChangedAt()).isNotNull();
        assertThat(saved.getRoles()).extracting(Role::getName).containsExactly("USER");

        verify(passwordHistory).record(saved, saved.getPasswordHash());
        assertThat(response.username()).isEqualTo("jane");
        assertThat(response.roles()).hasSize(1);
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("jane")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateUserRequest(
                                                "jane",
                                                "jane@ewos.local",
                                                "Str0ng!Pass",
                                                Set.of(),
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("jane@ewos.local")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateUserRequest(
                                                "jane",
                                                "jane@ewos.local",
                                                "Str0ng!Pass",
                                                Set.of(),
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsUnknownRoleIds() {
        UUID rid = UUID.randomUUID();
        when(roleRepository.findAllById(Set.of(rid))).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateUserRequest(
                                                "jane",
                                                "jane@ewos.local",
                                                "Str0ng!Pass",
                                                Set.of(rid),
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPropagatesPolicyFailure() {
        doThrow(new ApiException(HttpStatus.BAD_REQUEST, "policy"))
                .when(passwordPolicy)
                .validate("weak");

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateUserRequest(
                                                "jane", "jane@ewos.local", "weak", Set.of(), null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never()).save(any());
    }

    @Test
    void setEnabledFlipsFlag() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        UserResponse response = service.setEnabled(user.getId(), false);

        assertThat(user.isEnabled()).isFalse();
        assertThat(response.enabled()).isFalse();
    }

    @Test
    void updateChangesEmailAndRoles() {
        User user = existingUser();
        Role newRole = role("MANAGER");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(roleRepository.findAllById(Set.of(newRole.getId()))).thenReturn(List.of(newRole));

        UserResponse response =
                service.update(
                        user.getId(),
                        new UpdateUserRequest("new@ewos.local", Set.of(newRole.getId())));

        assertThat(user.getEmail()).isEqualTo("new@ewos.local");
        assertThat(user.getRoles()).extracting(Role::getName).containsExactly("MANAGER");
        assertThat(response.email()).isEqualTo("new@ewos.local");
    }

    @Test
    void updateRejectsEmailAlreadyTakenByAnotherUser() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("other@ewos.local")).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.update(
                                        user.getId(),
                                        new UpdateUserRequest("other@ewos.local", null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void resetPasswordValidatesPolicyReuseAndRecords() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        doNothing().when(passwordHistory).assertNotReused(eq(user), eq("Fresh!Pass1"));

        service.resetPassword(user.getId(), "Fresh!Pass1");

        verify(passwordPolicy).validate("Fresh!Pass1");
        verify(passwordHistory).assertNotReused(user, "Fresh!Pass1");
        verify(passwordHistory).record(user, "hash(Fresh!Pass1)");
        assertThat(user.getPasswordHash()).isEqualTo("hash(Fresh!Pass1)");
        assertThat(user.getPasswordChangedAt()).isNotNull();
    }

    @Test
    void changePasswordRequiresCorrectCurrentPassword() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-current", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(
                        () -> service.changePassword(user.getId(), "wrong-current", "Fresh!Pass1"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(passwordHistory, never()).record(any(), any());
    }

    @Test
    void changePasswordAppliesWhenCurrentMatches() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Right!Current1", user.getPasswordHash())).thenReturn(true);

        service.changePassword(user.getId(), "Right!Current1", "Fresh!Pass1");

        assertThat(user.getPasswordHash()).isEqualTo("hash(Fresh!Pass1)");
    }

    @Test
    void getByIdReturns404WhenMissing() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- fixtures -----------------------------------------------------------

    private static User existingUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("jane");
        user.setEmail("jane@ewos.local");
        user.setPasswordHash("hash(existing)");
        user.setEnabled(true);
        user.setAccountNonLocked(true);
        user.setRoles(new HashSet<>());
        return user;
    }

    private static Role role(String name) {
        Role r = new Role(name, name);
        r.setId(UUID.randomUUID());
        return r;
    }
}
