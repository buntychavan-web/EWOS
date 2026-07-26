package com.ewos.tenancy.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.tenancy.domain.UserTenantMembership;
import com.ewos.tenancy.infrastructure.persistence.UserTenantMembershipRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTenantMembershipFilterImplTest {

    @Mock UserTenantMembershipRepository memberships;

    private UserTenantMembershipFilterImpl filter;

    @BeforeEach
    void setUp() {
        filter = new UserTenantMembershipFilterImpl(memberships);
    }

    @Test
    void returnsOnlyUsersBelongingToTheGivenTenant() {
        UUID tenantId = UUID.randomUUID();
        UUID inTenant = UUID.randomUUID();
        UUID outOfTenant = UUID.randomUUID();
        UserTenantMembership membership = new UserTenantMembership();
        membership.setUserId(inTenant);
        membership.setTenantId(tenantId);
        when(memberships.findAllByUserIdInAndTenantId(Set.of(inTenant, outOfTenant), tenantId))
                .thenReturn(List.of(membership));

        Set<UUID> result = filter.filterToTenant(Set.of(inTenant, outOfTenant), tenantId);

        assertThat(result).containsExactly(inTenant);
    }

    @Test
    void emptyInputYieldsEmptyOutputWithoutQuerying() {
        assertThat(filter.filterToTenant(Set.of(), UUID.randomUUID())).isEmpty();
    }
}
