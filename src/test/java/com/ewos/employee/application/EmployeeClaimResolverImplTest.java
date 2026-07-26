package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeClaimResolverImplTest {

    @Mock EmployeeRepository employees;

    private EmployeeClaimResolverImpl resolver;

    @BeforeEach
    void setUp() {
        resolver = new EmployeeClaimResolverImpl(employees);
    }

    @Test
    void resolvesEmployeeIdWhenExactlyOneLinkExists() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Employee e = new Employee();
        e.setId(UUID.randomUUID());
        when(employees.findAllByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of(e));

        assertThat(resolver.resolveEmployeeId(userId, tenantId)).contains(e.getId());
    }

    @Test
    void emptyWhenNoLinkExists() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(employees.findAllByUserIdAndTenantId(userId, tenantId)).thenReturn(List.of());

        assertThat(resolver.resolveEmployeeId(userId, tenantId)).isEmpty();
    }

    @Test
    void emptyWhenMultipleLinksExistAcrossCompanies() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Employee first = new Employee();
        first.setId(UUID.randomUUID());
        Employee second = new Employee();
        second.setId(UUID.randomUUID());
        when(employees.findAllByUserIdAndTenantId(userId, tenantId))
                .thenReturn(List.of(first, second));

        assertThat(resolver.resolveEmployeeId(userId, tenantId)).isEmpty();
    }

    @Test
    void unrelatedResolutionsDoNotInterfere() {
        when(employees.findAllByUserIdAndTenantId(any(), any())).thenReturn(List.of());
        assertThat(resolver.resolveEmployeeId(UUID.randomUUID(), UUID.randomUUID())).isEmpty();
    }
}
