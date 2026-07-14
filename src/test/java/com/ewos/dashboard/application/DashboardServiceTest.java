package com.ewos.dashboard.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ewos.dashboard.api.dto.DashboardSummaryResponse;
import com.ewos.dashboard.infrastructure.DashboardCountsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock DashboardCountsRepository repository;
    @InjectMocks DashboardService service;

    @Test
    void summaryProjectsRepositoryTuple() {
        when(repository.loadCounts()).thenReturn(new long[] {0L, 42L, 0L, 7L});

        DashboardSummaryResponse resp = service.summary();

        assertThat(resp.employees()).isZero();
        assertThat(resp.users()).isEqualTo(42L);
        assertThat(resp.departments()).isZero();
        assertThat(resp.roles()).isEqualTo(7L);
    }

    @Test
    void summaryReturnsZerosWhenTablesEmpty() {
        when(repository.loadCounts()).thenReturn(new long[] {0L, 0L, 0L, 0L});

        DashboardSummaryResponse resp = service.summary();

        assertThat(resp.employees()).isZero();
        assertThat(resp.users()).isZero();
        assertThat(resp.departments()).isZero();
        assertThat(resp.roles()).isZero();
    }
}
