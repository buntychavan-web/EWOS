package com.ewos.workflow.application;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.identity.domain.Role;
import com.ewos.identity.infrastructure.persistence.RoleRepository;
import com.ewos.identity.infrastructure.persistence.UserRepository;
import com.ewos.workflow.domain.WorkflowActorType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves a dynamic approver role code (as configured on {@code
 * WorkflowState.defaultApproverRole}) to concrete actor(s) for a given subject employee, so a
 * workflow definition can say "the employee's manager" instead of a caller hard-coding one specific
 * employee id.
 *
 * <p>Two roles are resolved structurally, from the existing employee hierarchy:
 *
 * <ul>
 *   <li>{@code MANAGER} — {@code Employee.manager}.
 *   <li>{@code CEO} — walks the manager chain to its top (bounded at 20 hops against a malformed
 *       cycle); the employee with no manager is treated as the company's CEO. This assumes a
 *       single-root reporting hierarchy per company, which holds for every company seeded so far.
 * </ul>
 *
 * <p>Every other code — {@code HOD}, {@code HR}, {@code FINANCE}, or {@code CUSTOM:<name>} —
 * resolves by the same mechanism: look up a {@link Role} with that exact name visible to the tenant
 * (custom role first, system role second) and return every user holding it who also has an {@link
 * Employee} record in the target company. There is deliberately no separate "department head" data
 * model — building one is out of scope for this sprint; a tenant that wants an HOD approver creates
 * a role named {@code HOD} and assigns it to the right people, same as any other role-based
 * approver.
 */
@Component
@Transactional(readOnly = true)
public class ApproverResolver {

    private static final int MAX_HIERARCHY_HOPS = 20;
    private static final String CUSTOM_PREFIX = "CUSTOM:";

    private final EmployeeRepository employees;
    private final RoleRepository roles;
    private final UserRepository users;

    public ApproverResolver(
            EmployeeRepository employees, RoleRepository roles, UserRepository users) {
        this.employees = employees;
        this.roles = roles;
        this.users = users;
    }

    /** One resolved candidate approver. Several may come back for a role held by multiple users. */
    public record ResolvedApprover(WorkflowActorType actorType, UUID actorId) {}

    public List<ResolvedApprover> resolve(
            UUID tenantId, UUID companyId, UUID subjectEmployeeId, String approverRoleCode) {
        if (approverRoleCode == null || approverRoleCode.isBlank()) {
            return List.of();
        }
        String code = approverRoleCode.trim();
        if ("MANAGER".equalsIgnoreCase(code)) {
            return resolveManager(tenantId, subjectEmployeeId);
        }
        if ("CEO".equalsIgnoreCase(code)) {
            return resolveTopOfChain(tenantId, subjectEmployeeId);
        }
        String roleName =
                code.regionMatches(true, 0, CUSTOM_PREFIX, 0, CUSTOM_PREFIX.length())
                        ? code.substring(CUSTOM_PREFIX.length())
                        : code;
        return resolveByRole(tenantId, companyId, roleName);
    }

    private List<ResolvedApprover> resolveManager(UUID tenantId, UUID employeeId) {
        Employee employee = employees.findByIdAndTenantId(employeeId, tenantId).orElse(null);
        if (employee == null || employee.getManager() == null) {
            return List.of();
        }
        return List.of(
                new ResolvedApprover(WorkflowActorType.EMPLOYEE, employee.getManager().getId()));
    }

    private List<ResolvedApprover> resolveTopOfChain(UUID tenantId, UUID employeeId) {
        Employee current = employees.findByIdAndTenantId(employeeId, tenantId).orElse(null);
        if (current == null) {
            return List.of();
        }
        for (int hop = 0; hop < MAX_HIERARCHY_HOPS && current.getManager() != null; hop++) {
            Employee next =
                    employees
                            .findByIdAndTenantId(current.getManager().getId(), tenantId)
                            .orElse(null);
            if (next == null) {
                break;
            }
            current = next;
        }
        return List.of(new ResolvedApprover(WorkflowActorType.EMPLOYEE, current.getId()));
    }

    private List<ResolvedApprover> resolveByRole(UUID tenantId, UUID companyId, String roleName) {
        Role role = roles.findVisibleByName(tenantId, roleName).stream().findFirst().orElse(null);
        if (role == null) {
            return List.of();
        }
        return users.findAllByRolesId(role.getId()).stream()
                .filter(u -> employees.existsByCompanyIdAndUserId(companyId, u.getId()))
                .map(u -> new ResolvedApprover(WorkflowActorType.USER, u.getId()))
                .toList();
    }
}
