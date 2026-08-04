package com.ewos.identity.application;

import com.ewos.identity.api.UserMapper;
import com.ewos.identity.api.dto.CreateUserRequest;
import com.ewos.identity.api.dto.UpdateUserRequest;
import com.ewos.identity.api.dto.UserResponse;
import com.ewos.identity.api.dto.UserSearchCriteria;
import com.ewos.identity.domain.Role;
import com.ewos.identity.domain.User;
import com.ewos.identity.domain.events.IdentityEvent;
import com.ewos.identity.domain.events.IdentityEventType;
import com.ewos.identity.infrastructure.persistence.RefreshTokenRepository;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.identity.infrastructure.persistence.UserSpecifications;
import com.ewos.shared.exception.ApiException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicy;
    private final PasswordHistoryService passwordHistory;
    private final UserMapper userMapper;
    private final RequestTenantContext requestTenantContext;
    private final DefaultTenantMembershipProvisioner tenantMembershipProvisioner;
    private final TenantMembershipFilter tenantMembershipFilter;
    private final TenantClaimResolver tenantClaimResolver;
    private final IdentityEventPublisher events;
    private final RefreshTokenRepository refreshTokenRepository;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PasswordPolicyValidator passwordPolicy,
            PasswordHistoryService passwordHistory,
            UserMapper userMapper,
            RequestTenantContext requestTenantContext,
            DefaultTenantMembershipProvisioner tenantMembershipProvisioner,
            TenantMembershipFilter tenantMembershipFilter,
            TenantClaimResolver tenantClaimResolver,
            IdentityEventPublisher events,
            RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.passwordHistory = passwordHistory;
        this.userMapper = userMapper;
        this.requestTenantContext = requestTenantContext;
        this.tenantMembershipProvisioner = tenantMembershipProvisioner;
        this.tenantMembershipFilter = tenantMembershipFilter;
        this.tenantClaimResolver = tenantClaimResolver;
        this.events = events;
        this.refreshTokenRepository = refreshTokenRepository;
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
        // Sprint 21 UAT — without this, a brand-new user (created directly here, or via
        // EmployeeIdentityLinkService#provisionUser, which delegates to this method) had no
        // tenant membership at all and could never resolve a tenant for any request afterward.
        // Assign the creating admin's own tenant; fall back to the platform bootstrap tenant only
        // if the caller somehow has none (defensive — every authenticated admin call has one).
        UUID callerTenantId = requestTenantContext.currentTenantId().orElse(null);
        if (callerTenantId != null) {
            tenantMembershipProvisioner.ensureMembership(saved.getId(), callerTenantId);
        } else {
            tenantMembershipProvisioner.ensureDefaultMembership(saved.getId());
        }
        return userMapper.toResponse(saved);
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
        return userMapper.toResponse(user);
    }

    public UserResponse setEnabled(UUID id, boolean enabled) {
        User user = requireUser(id);
        user.setEnabled(enabled);
        if (!enabled) {
            publishIdentityEvent(IdentityEventType.ACCOUNT_DISABLED, user.getId());
        }
        return userMapper.toResponse(user);
    }

    public void resetPassword(UUID id, String newPassword) {
        User user = requireUser(id);
        applyNewPassword(user, newPassword);
        publishIdentityEvent(IdentityEventType.PASSWORD_RESET_BY_ADMIN, user.getId());
    }

    /**
     * Sprint 24F — {@code userId} doubles as the recipient actor id (see {@link IdentityEvent}'s
     * javadoc), so the only lookup needed is the affected user's own tenant — not the caller's,
     * since a {@code SYSTEM_ADMIN} may act on a user in any tenant (see {@link
     * #requireTenantAccess}).
     */
    private void publishIdentityEvent(IdentityEventType type, UUID userId) {
        tenantClaimResolver
                .resolveTenantId(userId)
                .ifPresent(
                        tenantId ->
                                events.publish(
                                        new IdentityEvent(
                                                type,
                                                tenantId,
                                                userId,
                                                currentUserId(),
                                                Instant.now())));
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
        return userMapper.toResponse(requireUser(id));
    }

    /**
     * Soft-deletes the user. Hibernate's {@code @SQLDelete} intercepts the DELETE and sets {@code
     * deleted_at = NOW()}. Partial unique indexes on {@code username} / {@code email} keep those
     * values reusable by future active users.
     */
    public void softDelete(UUID id) {
        User user = requireUser(id);
        userRepository.delete(user);
    }

    /**
     * Sprint 24F — {@code User} deliberately has no {@code tenant_id} column of its own (see the
     * class javadoc on {@link com.ewos.identity.domain.User}); tenant membership lives in {@code
     * user_tenant_memberships}, owned by {@code com.ewos.tenancy}. Before this fix every method
     * below operated platform-wide with no tenant check at all — safe only by accident, because
     * today only {@code SYSTEM_ADMIN} (a deliberately platform-wide role) holds {@code
     * USER_READ}/{@code USER_WRITE}/{@code USER_DELETE}. The moment a tenant-scoped role is ever
     * granted one of those permissions, it would be able to read/edit/disable/delete/reset-password
     * on any user on the platform. {@code SYSTEM_ADMIN} itself keeps unscoped access, matching its
     * existing platform-support role elsewhere (see {@code TenantAccessGrant}); every other caller
     * is narrowed to their own tenant via {@link TenantMembershipFilter}, the same port {@code
     * RoleImpactService} already uses for the identical class of leak (Sprint 1.4 Finding 1).
     */
    @Transactional(readOnly = true)
    public Page<UserResponse> search(UserSearchCriteria criteria, Pageable pageable) {
        Specification<User> spec = UserSpecifications.matching(criteria);
        if (!hasAuthority(SYSTEM_ADMIN)) {
            Set<UUID> tenantUserIds =
                    tenantMembershipFilter.userIdsForTenant(requireCallerTenantId());
            spec =
                    spec.and(
                            (root, query, cb) ->
                                    tenantUserIds.isEmpty()
                                            ? cb.disjunction()
                                            : root.get("id").in(tenantUserIds));
        }
        return userRepository.findAll(spec, pageable).map(userMapper::toResponse);
    }

    private void applyNewPassword(User user, String newRawPassword) {
        passwordPolicy.validate(newRawPassword);
        passwordHistory.assertNotReused(user, newRawPassword);
        String hash = passwordEncoder.encode(newRawPassword);
        user.setPasswordHash(hash);
        user.setPasswordChangedAt(Instant.now());
        passwordHistory.record(user, hash);
        // A password change/reset must end every other session — otherwise a refresh token
        // captured before the change (e.g. by whoever/whatever prompted the reset) keeps working
        // indefinitely. The access token itself still lives out its short TTL, same as logout.
        refreshTokenRepository.revokeAllForUser(user.getId(), "password_changed");
    }

    private Set<Role> resolveRoles(Set<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        if (roles.size() != roleIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "One or more role IDs are unknown");
        }
        // Sprint 1.4: a role scoped to another tenant may never be assigned, even by an admin who
        // can
        // otherwise see and manage users platform-wide. System roles (tenantId == null) are always
        // assignable.
        UUID callerTenantId = requestTenantContext.currentTenantId().orElse(null);
        for (Role role : roles) {
            if (role.getTenantId() != null && !role.getTenantId().equals(callerTenantId)) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "Role '" + role.getName() + "' does not belong to your tenant");
            }
        }
        return roles;
    }

    private User requireUser(UUID id) {
        User user =
                userRepository
                        .findById(id)
                        .orElseThrow(
                                () -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        requireTenantAccess(user);
        return user;
    }

    /**
     * A caller may always resolve their own record — this is what keeps {@code GET /auth/me} and
     * self-service password change working for a brand-new account that has no tenant membership
     * yet ({@link TenantClaimResolver}'s javadoc calls this "a real, admin-actionable state, not an
     * error"); without this check such a user would 401 on their own {@code /auth/me} the moment
     * tenant scoping applies. {@code SYSTEM_ADMIN} bypasses tenant scoping by design for every
     * other user (see {@link #search}'s javadoc). Any other caller who resolves a user outside
     * their own tenant gets the same 404 a genuinely missing id would — a 403 would confirm the id
     * exists somewhere on the platform, which is exactly the cross-tenant existence leak this fix
     * closes.
     */
    private void requireTenantAccess(User user) {
        if (user.getId().equals(currentUserId()) || hasAuthority(SYSTEM_ADMIN)) {
            return;
        }
        UUID callerTenantId = requireCallerTenantId();
        if (!tenantMembershipFilter
                .filterToTenant(Set.of(user.getId()), callerTenantId)
                .contains(user.getId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private UUID requireCallerTenantId() {
        return requestTenantContext
                .currentTenantId()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Tenant context is required for this action"));
    }

    private static boolean hasAuthority(String authority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    private static UUID currentUserId() {
        try {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return null;
            }
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
