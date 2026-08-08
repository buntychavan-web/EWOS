package com.ewos.employee.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.MssFieldVisibilityConfig;
import com.ewos.employee.infrastructure.persistence.MssFieldVisibilityConfigRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MssFieldVisibilityServiceTest {

    @Mock MssFieldVisibilityConfigRepository configs;

    private MssFieldVisibilityService service;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MssFieldVisibilityService(configs);
    }

    @Test
    void canManagerViewIsDefaultDenyWhenNoConfigRowExists() {
        when(configs.findByTenantIdAndFieldName(tenantId, "salary")).thenReturn(Optional.empty());

        assertThat(service.canManagerView(tenantId, "salary")).isFalse();
    }

    @Test
    void canManagerViewReflectsAnExplicitlyConfiguredTrueRow() {
        MssFieldVisibilityConfig config = new MssFieldVisibilityConfig();
        config.setTenantId(tenantId);
        config.setFieldName("leaveBalance");
        config.setManagerCanView(true);
        when(configs.findByTenantIdAndFieldName(tenantId, "leaveBalance"))
                .thenReturn(Optional.of(config));

        assertThat(service.canManagerView(tenantId, "leaveBalance")).isTrue();
    }

    @Test
    void canManagerViewReflectsAnExplicitlyConfiguredFalseRow() {
        MssFieldVisibilityConfig config = new MssFieldVisibilityConfig();
        config.setTenantId(tenantId);
        config.setFieldName("bankAccountNumber");
        config.setManagerCanView(false);
        when(configs.findByTenantIdAndFieldName(tenantId, "bankAccountNumber"))
                .thenReturn(Optional.of(config));

        assertThat(service.canManagerView(tenantId, "bankAccountNumber")).isFalse();
    }

    @Test
    void setVisibilityCreatesANewRowWhenNoneExists() {
        when(configs.findByTenantIdAndFieldName(tenantId, "phone")).thenReturn(Optional.empty());
        when(configs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MssFieldVisibilityConfig result = service.setVisibility(tenantId, "phone", true);

        assertThat(result.getTenantId()).isEqualTo(tenantId);
        assertThat(result.getFieldName()).isEqualTo("phone");
        assertThat(result.isManagerCanView()).isTrue();
    }

    @Test
    void setVisibilityUpdatesTheExistingRowRatherThanDuplicatingIt() {
        MssFieldVisibilityConfig existing = new MssFieldVisibilityConfig();
        existing.setTenantId(tenantId);
        existing.setFieldName("dateOfBirth");
        existing.setManagerCanView(false);
        when(configs.findByTenantIdAndFieldName(tenantId, "dateOfBirth"))
                .thenReturn(Optional.of(existing));
        when(configs.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MssFieldVisibilityConfig result = service.setVisibility(tenantId, "dateOfBirth", true);

        assertThat(result).isSameAs(existing);
        assertThat(result.isManagerCanView()).isTrue();
    }
}
