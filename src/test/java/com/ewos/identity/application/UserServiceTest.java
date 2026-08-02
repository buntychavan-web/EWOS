package com.ewos.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
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
import com.ewos.identity.api.dto.UserSearchCriteria;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final UUID DEFAULT_TENANT = UUID.randomUUID();

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordPolicyValidator passwordPolicy;
    @Mock PasswordHistoryService passwordHistory;
    @Mock RequestTenantContext requestTenantContext;
    @Mock DefaultTenantMembershipProvisioner tenantMembershipProvisioner;
    @Mock TenantMembershipFilter tenantMembershipFilter;
    @Mock TenantClaimResolver tenantClaimResolver;
    @Mock IdentityEventPublisher identityEventPublisher;

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
                        new com.ewos.identity.api.UserMapper(),
                        requestTenantContext,
                        tenantMembershipProvisioner,
                        tenantMembershipFilter,
                        tenantClaimResolver,
                        identityEventPublisher);
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
        // Default: caller resolves to DEFAULT_TENANT and every user requested is found to be a
        // member of it — i.e. "same tenant as the caller", the common case every pre-existing test
        // below assumes. Tests exercising the Sprint 24F tenant-isolation fix itself override this.
        lenient()
                .when(requestTenantContext.currentTenantId())
                .thenReturn(Optional.of(DEFAULT_TENANT));
        lenient()
                .when(tenantMembershipFilter.filterToTenant(anySet(), eq(DEFAULT_TENANT)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient()
                .when(tenantClaimResolver.resolveTenantId(any()))
                .thenReturn(Optional.of(DEFAULT_TENANT));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(UUID userId, String... authorities) {
        List<? extends GrantedAuthority> granted =
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new TestingAuthenticationToken(userId.toString(), null, granted));
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
    void createAssignsNewUserToCallersTenant() {
        // Sprint 21 UAT — a brand-new user must land in the creating admin's own tenant so it can
        // resolve a tenant for its own requests afterward, not always the platform bootstrap
        // tenant.
        Role role = role("USER");
        UUID callerTenant = UUID.randomUUID();
        when(roleRepository.findAllById(Set.of(role.getId()))).thenReturn(List.of(role));
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.of(callerTenant));

        UserResponse response =
                service.create(
                        new CreateUserRequest(
                                "jane",
                                "jane@ewos.local",
                                "Str0ng!Pass",
                                Set.of(role.getId()),
                                null));

        ArgumentCaptor<UUID> userIdCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(tenantMembershipProvisioner)
                .ensureMembership(userIdCaptor.capture(), eq(callerTenant));
        assertThat(userIdCaptor.getValue()).isEqualTo(response.id());
        verify(tenantMembershipProvisioner, never()).ensureDefaultMembership(any());
    }

    @Test
    void createFallsBackToDefaultMembershipWhenCallerHasNoTenant() {
        Role role = role("USER");
        when(roleRepository.findAllById(Set.of(role.getId()))).thenReturn(List.of(role));
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());

        UserResponse response =
                service.create(
                        new CreateUserRequest(
                                "jane",
                                "jane@ewos.local",
                                "Str0ng!Pass",
                                Set.of(role.getId()),
                                null));

        verify(tenantMembershipProvisioner).ensureDefaultMembership(response.id());
        verify(tenantMembershipProvisioner, never()).ensureMembership(any(), any());
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
    void disablingAUserPublishesAccountDisabledEvent() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.setEnabled(user.getId(), false);

        ArgumentCaptor<com.ewos.identity.domain.events.IdentityEvent> captor =
                ArgumentCaptor.forClass(com.ewos.identity.domain.events.IdentityEvent.class);
        verify(identityEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().eventType())
                .isEqualTo(com.ewos.identity.domain.events.IdentityEventType.ACCOUNT_DISABLED);
        assertThat(captor.getValue().userId()).isEqualTo(user.getId());
    }

    @Test
    void enablingAUserPublishesNoEvent() {
        User user = existingUser();
        user.setEnabled(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service.setEnabled(user.getId(), true);

        verify(identityEventPublisher, never()).publish(any());
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
    void createRejectsRoleScopedToAnotherTenant() {
        UUID callerTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();
        Role foreignRole = role("PAYROLL_REVIEWER");
        foreignRole.setTenantId(otherTenant);
        when(roleRepository.findAllById(Set.of(foreignRole.getId())))
                .thenReturn(List.of(foreignRole));
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.of(callerTenant));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateUserRequest(
                                                "jane",
                                                "jane@ewos.local",
                                                "Str0ng!Pass",
                                                Set.of(foreignRole.getId()),
                                                null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void createAllowsRoleScopedToCallersOwnTenant() {
        UUID callerTenant = UUID.randomUUID();
        Role ownRole = role("PAYROLL_REVIEWER");
        ownRole.setTenantId(callerTenant);
        when(roleRepository.findAllById(Set.of(ownRole.getId()))).thenReturn(List.of(ownRole));
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.of(callerTenant));

        UserResponse response =
                service.create(
                        new CreateUserRequest(
                                "jane",
                                "jane@ewos.local",
                                "Str0ng!Pass",
                                Set.of(ownRole.getId()),
                                null));

        assertThat(response.roles()).hasSize(1);
    }

    @Test
    void createAllowsSystemRoleRegardlessOfCallerTenant() {
        Role systemRole = role("SYSTEM_ADMIN");
        when(roleRepository.findAllById(Set.of(systemRole.getId())))
                .thenReturn(List.of(systemRole));

        UserResponse response =
                service.create(
                        new CreateUserRequest(
                                "jane",
                                "jane@ewos.local",
                                "Str0ng!Pass",
                                Set.of(systemRole.getId()),
                                null));

        assertThat(response.roles()).hasSize(1);
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

        ArgumentCaptor<com.ewos.identity.domain.events.IdentityEvent> captor =
                ArgumentCaptor.forClass(com.ewos.identity.domain.events.IdentityEvent.class);
        verify(identityEventPublisher).publish(captor.capture());
        assertThat(captor.getValue().eventType())
                .isEqualTo(
                        com.ewos.identity.domain.events.IdentityEventType.PASSWORD_RESET_BY_ADMIN);
        assertThat(captor.getValue().userId()).isEqualTo(user.getId());
    }

    @Test
    void selfServiceChangePasswordPublishesNoAdminResetEvent() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Right!Current1", user.getPasswordHash())).thenReturn(true);

        service.changePassword(user.getId(), "Right!Current1", "Fresh!Pass1");

        verify(identityEventPublisher, never()).publish(any());
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

    // --- Sprint 24F: tenant isolation -----------------------------------------

    @Test
    void getByIdReturns404ForUserInAnotherTenant() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        // Overrides the shared setUp() pass-through stub: this user has no membership in
        // DEFAULT_TENANT, the caller's own tenant.
        when(tenantMembershipFilter.filterToTenant(Set.of(user.getId()), DEFAULT_TENANT))
                .thenReturn(Set.of());
        authenticateAs(UUID.randomUUID(), "USER_READ");

        assertThatThrownBy(() -> service.getById(user.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getByIdSucceedsForUserInCallersOwnTenant() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        authenticateAs(UUID.randomUUID(), "USER_READ");

        UserResponse response = service.getById(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
    }

    @Test
    void systemAdminBypassesTenantScopingOnGetById() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        // Caller has no resolvable tenant at all (platform-support scenario) — SYSTEM_ADMIN must
        // still be able to reach any user, without even consulting tenant context.
        lenient().when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), "SYSTEM_ADMIN");

        UserResponse response = service.getById(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
    }

    @Test
    void nonAdminWithNoTenantContextGets401NotLeakedExistence() {
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());
        authenticateAs(UUID.randomUUID(), "USER_READ");

        assertThatThrownBy(() -> service.getById(user.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void callerCanAlwaysResolveTheirOwnRecordEvenWithNoTenantMembershipYet() {
        // Mirrors GET /auth/me for a brand-new account mid-provisioning (TenantClaimResolver's
        // documented "empty is a real, admin-actionable state, not an error").
        User user = existingUser();
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        lenient().when(requestTenantContext.currentTenantId()).thenReturn(Optional.empty());
        authenticateAs(user.getId());

        UserResponse response = service.getById(user.getId());

        assertThat(response.id()).isEqualTo(user.getId());
    }

    @Test
    void searchScopesResultsToCallersTenantForNonAdmin() {
        authenticateAs(UUID.randomUUID(), "USER_READ");
        UUID inTenant = UUID.randomUUID();
        when(tenantMembershipFilter.userIdsForTenant(DEFAULT_TENANT)).thenReturn(Set.of(inTenant));
        when(userRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        service.search(emptyCriteria(), PageRequest.of(0, 20));

        verify(tenantMembershipFilter).userIdsForTenant(DEFAULT_TENANT);
    }

    @Test
    void searchIsUnscopedForSystemAdmin() {
        authenticateAs(UUID.randomUUID(), "SYSTEM_ADMIN");
        when(userRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        service.search(emptyCriteria(), PageRequest.of(0, 20));

        verify(tenantMembershipFilter, never()).userIdsForTenant(any());
    }

    private static UserSearchCriteria emptyCriteria() {
        return new UserSearchCriteria(null, null, null, null, null, null);
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
