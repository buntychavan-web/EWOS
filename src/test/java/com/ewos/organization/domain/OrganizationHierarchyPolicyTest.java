package com.ewos.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ewos.shared.exception.ApiException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class OrganizationHierarchyPolicyTest {

    private final OrganizationHierarchyPolicy policy = new OrganizationHierarchyPolicy();

    @Test
    void nullParentIsAlwaysValid() {
        OrganizationUnit child = unit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        policy.assertValidParent(child, null); // no throw
    }

    @Test
    void differentTenantIsRejected() {
        UUID sharedCo = UUID.randomUUID();
        OrganizationUnit child = unit(UUID.randomUUID(), UUID.randomUUID(), sharedCo);
        OrganizationUnit parent = unit(UUID.randomUUID(), UUID.randomUUID(), sharedCo);
        assertThatThrownBy(() -> policy.assertValidParent(child, parent))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("tenant");
    }

    @Test
    void differentCompanyIsRejected() {
        UUID sharedTenant = UUID.randomUUID();
        OrganizationUnit child = unit(UUID.randomUUID(), sharedTenant, UUID.randomUUID());
        OrganizationUnit parent = unit(UUID.randomUUID(), sharedTenant, UUID.randomUUID());
        assertThatThrownBy(() -> policy.assertValidParent(child, parent))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("company");
    }

    @Test
    void closedParentIsRejected() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        OrganizationUnit child = unit(UUID.randomUUID(), tenant, company);
        OrganizationUnit parent = unit(UUID.randomUUID(), tenant, company);
        parent.setStatus(OrganizationUnitStatus.CLOSED);
        assertThatThrownBy(() -> policy.assertValidParent(child, parent))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cycleIsRejected() {
        UUID tenant = UUID.randomUUID();
        UUID company = UUID.randomUUID();
        OrganizationUnit a = unit(UUID.randomUUID(), tenant, company);
        OrganizationUnit b = unit(UUID.randomUUID(), tenant, company);
        OrganizationUnit c = unit(UUID.randomUUID(), tenant, company);
        // Existing chain: A -> B -> C ; then try to set A.parent = C (would create cycle).
        c.setParent(b);
        b.setParent(a);
        assertThatThrownBy(() -> policy.assertValidParent(a, c))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void closableRejectsWhenChildrenPresent() {
        OrganizationUnit u = unit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        assertThatThrownBy(() -> policy.assertClosable(u, 3))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("3 active children");
    }

    @Test
    void closableRejectsAlreadyClosed() {
        OrganizationUnit u = unit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        u.setStatus(OrganizationUnitStatus.CLOSED);
        assertThatThrownBy(() -> policy.assertClosable(u, 0))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    void closableAcceptsWhenLeafAndActive() {
        OrganizationUnit u = unit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        policy.assertClosable(u, 0); // no throw
        assertThat(u.getStatus()).isEqualTo(OrganizationUnitStatus.ACTIVE);
    }

    private static OrganizationUnit unit(UUID id, UUID tenant, UUID company) {
        OrganizationUnit u = new OrganizationUnit();
        u.setId(id);
        u.setTenantId(tenant);
        u.setCompanyId(company);
        u.setCode("U-" + id.toString().substring(0, 6));
        u.setName("Unit " + id.toString().substring(0, 6));
        u.setEffectiveFrom(LocalDate.now());
        return u;
    }
}
