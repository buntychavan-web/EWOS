package com.ewos.identity.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.ewos.AbstractIntegrationTest;
import com.ewos.identity.domain.Permission;
import com.ewos.identity.domain.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Regression test for a Sprint P9 production-readiness finding: {@code Role} and {@code Permission}
 * (like {@code User}) are versioned entities with a custom {@code @SQLDelete} SQL string. Hibernate
 * always binds the entity's {@code @Version} value as a <em>second</em> JDBC parameter for a custom
 * {@code @SQLDelete} on a versioned entity, whether or not the SQL string itself references it — a
 * one-placeholder {@code @SQLDelete} throws {@code PSQLException: column index is out of range: 2,
 * number of columns: 1} on every delete. Both entities' delete SQL already has the required second
 * placeholder; this test exercises the real generated SQL against a live Postgres so a future edit
 * that drops it fails loudly here instead of surfacing as a 500 in production.
 */
class SoftDeleteRegressionTest extends AbstractIntegrationTest {

    @Autowired RoleRepository roles;
    @Autowired PermissionRepository permissions;

    @Test
    void deletingARoleSoftDeletesItInsteadOfThrowing() {
        Role role = new Role("REGRESSION_ROLE_" + UUID.randomUUID(), "Regression test role");
        role.setTenantId(UUID.randomUUID());
        Role saved = roles.saveAndFlush(role);
        UUID id = saved.getId();

        roles.delete(saved);
        roles.flush();

        assertThat(roles.findById(id)).isEmpty();
    }

    @Test
    void deletingAPermissionSoftDeletesItInsteadOfThrowing() {
        Permission permission =
                new Permission(
                        "REGRESSION_PERM_" + UUID.randomUUID(), "Regression test permission");
        Permission saved = permissions.saveAndFlush(permission);
        UUID id = saved.getId();

        permissions.delete(saved);
        permissions.flush();

        assertThat(permissions.findById(id)).isEmpty();
    }
}
