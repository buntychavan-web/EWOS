package com.ewos.tenancy.application;

import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.domain.ClientAssignment;
import com.ewos.tenancy.domain.Company;
import com.ewos.tenancy.domain.UserTenantMembership;
import com.ewos.tenancy.infrastructure.persistence.ClientAssignmentRepository;
import com.ewos.tenancy.infrastructure.persistence.CompanyRepository;
import com.ewos.tenancy.infrastructure.persistence.UserTenantMembershipRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * The Chinese Wall. Resolves the current authenticated user's client access from {@link
 * ClientAssignment} rows, checked in real time on every call rather than baked into the JWT — a
 * revoked assignment takes effect on the caller's very next request instead of waiting up to the
 * 15-minute access-token TTL for a stale claim to expire.
 *
 * <p>{@code CLIENT_ADMIN} bypasses per-client scoping entirely, mirroring the existing platform
 * convention of an admin-tier authority short-circuiting fine-grained checks (see how {@code
 * SYSTEM_ADMIN} is seeded with every permission rather than the code special-casing it).
 *
 * <p>A user who holds none of the calling module's permissions never reaches this guard at all
 * (blocked earlier by {@code @PreAuthorize}); a user who holds the permission but has zero {@link
 * ClientAssignment} rows sees zero clients — fail-closed by construction, not by convention.
 *
 * <p>Sprint 21 UAT — {@link ClientAssignment} exists for exactly one scenario: a {@code
 * payroll_service_providers} row's own staff, servicing multiple client companies, who must be kept
 * from crossing between clients they aren't assigned to (Sprint 14.2). It was never meant to gate
 * an ordinary tenant's own employees, managers, or HR/admin staff acting on their own company's
 * data — those users aren't "provider staff," they simply belong to the tenant that owns the
 * company. Sprint 1.2 rolled this guard out platform-wide (Leave, Attendance, Payroll, ...) without
 * that carve-out, so in any tenant onboarded without also hand-provisioning {@code
 * ClientAssignment} rows for every HR/manager user (which nothing in normal onboarding does), every
 * one of those modules 403'd for everyone except the single user holding {@code CLIENT_ADMIN}.
 * {@link #requireAccessForCompany(UUID)} now also bypasses for a caller who is simply a member of
 * the company's own tenant — the Chinese Wall still fully applies to a genuine external provider
 * operator, who has no {@link UserTenantMembership} in the client's tenant at all.
 */
@Component
public class ClientAccessGuard {

    private final ClientAssignmentRepository repository;
    private final CompanyRepository companyRepository;
    private final UserTenantMembershipRepository memberships;

    public ClientAccessGuard(
            ClientAssignmentRepository repository,
            CompanyRepository companyRepository,
            UserTenantMembershipRepository memberships) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.memberships = memberships;
    }

    /**
     * True when the caller holds {@code CLIENT_ADMIN} and therefore bypasses per-client scoping.
     */
    public boolean hasUnrestrictedAccess() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("CLIENT_ADMIN"::equals);
    }

    /**
     * The set of client ids the current user may access right now. Only meaningful when {@link
     * #hasUnrestrictedAccess()} is false — callers with unrestricted access should not filter by
     * this set at all.
     */
    public Set<UUID> accessibleClientIds() {
        UUID userId = currentUserId();
        if (userId == null) {
            return Set.of();
        }
        List<ClientAssignment> active = repository.findActiveForUser(userId, LocalDate.now());
        return active.stream().map(a -> a.getClient().getId()).collect(Collectors.toSet());
    }

    /**
     * Throws 403 unless the caller has unrestricted access or an active assignment to this client.
     */
    public void requireAccess(UUID clientId) {
        if (hasUnrestrictedAccess()) {
            return;
        }
        if (!accessibleClientIds().contains(clientId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not authorized for this client");
        }
    }

    /**
     * Resolves the client that owns {@code companyId} and delegates to {@link
     * #requireAccess(UUID)}. This is what Sprint 14.2 uses to Chinese-Wall-scope Payroll: every
     * payroll table already carries a {@code company_id}, and {@code companies.client_id} (Sprint
     * 14.1) is the missing link — no new column on any Payroll table was needed.
     */
    public void requireAccessForCompany(UUID companyId) {
        if (hasUnrestrictedAccess()) {
            return;
        }
        Optional<Company> company = companyRepository.findById(companyId);
        if (company.isPresent() && isMemberOfCompanysOwnTenant(company.get())) {
            return;
        }
        UUID clientId = company.map(c -> c.getClient().getId()).orElse(null);
        if (clientId == null || !accessibleClientIds().contains(clientId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Not authorized for this company");
        }
    }

    /**
     * True when the current caller belongs to the same tenant that owns {@code company} — an
     * ordinary employee/manager/HR-admin of that tenant, not an external provider operator. See the
     * class-level note: only a caller with no membership in the company's own tenant is a genuine
     * Chinese-Wall subject.
     */
    private boolean isMemberOfCompanysOwnTenant(Company company) {
        UUID userId = currentUserId();
        if (userId == null) {
            return false;
        }
        return memberships
                .findByUserId(userId)
                .map(UserTenantMembership::getTenantId)
                .map(tenantId -> tenantId.equals(company.getTenantId()))
                .orElse(false);
    }

    /**
     * Sprint 21 UAT — same as {@link #requireAccessForCompany(UUID)}, but also lets the call
     * through when the caller is acting on their own employee record. This Chinese Wall exists to
     * stop a provider's staff from crossing between clients they don't serve; it was never meant to
     * gate an ordinary employee's self-service view of their own data behind a {@link
     * ClientAssignment} grant, which self-service actors have no reason to ever hold. Without this,
     * every employee without an explicit assignment was locked out of their own leave/attendance
     * self-service the moment their company was linked to a client — the default for any onboarded
     * tenant.
     */
    public void requireAccessForCompany(UUID companyId, UUID subjectEmployeeId) {
        UUID callerEmployeeId = currentEmployeeId();
        if (callerEmployeeId != null && callerEmployeeId.equals(subjectEmployeeId)) {
            return;
        }
        requireAccessForCompany(companyId);
    }

    /**
     * Guards every distinct company id present in a Payroll result set — the shape a "list by
     * employeeId/runId/payslipId" query returns, where the caller has no single companyId to check
     * up front. A run/payslip/etc. belongs to exactly one company in practice, so this is normally
     * one check; it still holds if that ever isn't true.
     */
    public void requireAccessForCompanies(Collection<UUID> companyIds) {
        for (UUID companyId : new LinkedHashSet<>(companyIds)) {
            requireAccessForCompany(companyId);
        }
    }

    /** Self-service counterpart of {@link #requireAccessForCompanies(Collection)}. */
    public void requireAccessForCompanies(Collection<UUID> companyIds, UUID subjectEmployeeId) {
        UUID callerEmployeeId = currentEmployeeId();
        if (callerEmployeeId != null && callerEmployeeId.equals(subjectEmployeeId)) {
            return;
        }
        requireAccessForCompanies(companyIds);
    }

    /**
     * Same idiom {@code AuditorProvider} uses to resolve the current user: the JWT filter puts the
     * user's UUID string into {@code Authentication#getName()}.
     */
    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return null;
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * The employee linked to the current request's authenticated user, if any. Sourced from the
     * same JWT-derived request attribute {@code com.ewos.employee.application.EmployeeContext}
     * reads — duplicated here (rather than depending on the employee module) to keep this guard's
     * dependency direction unchanged: {@code com.ewos.employee} already depends on {@code
     * com.ewos.tenancy}, not the other way around.
     */
    private static UUID currentEmployeeId() {
        Object attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return null;
        }
        Object value =
                servletAttributes.getRequest().getAttribute("com.ewos.employee.currentEmployeeId");
        return value instanceof UUID employeeId ? employeeId : null;
    }
}
