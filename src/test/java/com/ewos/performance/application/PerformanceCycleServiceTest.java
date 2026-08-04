package com.ewos.performance.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.performance.api.PerformanceMapper;
import com.ewos.performance.domain.PerformanceCycle;
import com.ewos.performance.domain.PerformanceCycleLifecyclePolicy;
import com.ewos.performance.domain.PerformanceCycleStatus;
import com.ewos.performance.domain.PerformanceCycleTransition;
import com.ewos.performance.infrastructure.persistence.PerformanceCycleRepository;
import com.ewos.performance.infrastructure.persistence.PerformanceCycleTransitionRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PerformanceCycleServiceTest {

    @Mock PerformanceCycleRepository cycles;
    @Mock PerformanceCycleTransitionRepository transitions;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private PerformanceCycleService service;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        service =
                new PerformanceCycleService(
                        cycles,
                        transitions,
                        new PerformanceCycleLifecyclePolicy(),
                        new PerformanceMapper(),
                        events,
                        guard);
        tenantId = UUID.randomUUID();
    }

    private PerformanceCycle cycle(PerformanceCycleStatus status) {
        PerformanceCycle c = new PerformanceCycle();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setCompanyId(UUID.randomUUID());
        c.setStatus(status);
        return c;
    }

    @Test
    void transitionRejectsAnIllegalJump() {
        PerformanceCycle c = cycle(PerformanceCycleStatus.DRAFT);
        when(cycles.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.transition(
                                        tenantId, c.getId(), PerformanceCycleStatus.CLOSED, null))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(transitions, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void transitionRecordsAnAuditRowAndAdvancesStatus() {
        PerformanceCycle c = cycle(PerformanceCycleStatus.DRAFT);
        when(cycles.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        service.transition(tenantId, c.getId(), PerformanceCycleStatus.OPEN, "go live");

        assertThat(c.getStatus()).isEqualTo(PerformanceCycleStatus.OPEN);
        ArgumentCaptor<PerformanceCycleTransition> captor =
                ArgumentCaptor.forClass(PerformanceCycleTransition.class);
        verify(transitions).save(captor.capture());
        PerformanceCycleTransition recorded = captor.getValue();
        assertThat(recorded.getFromStatus()).isEqualTo(PerformanceCycleStatus.DRAFT);
        assertThat(recorded.getToStatus()).isEqualTo(PerformanceCycleStatus.OPEN);
        assertThat(recorded.getNotes()).isEqualTo("go live");
        assertThat(recorded.getCycleId()).isEqualTo(c.getId());
    }

    @Test
    void transitionHistoryDelegatesToRepositoryOrderedByTime() {
        PerformanceCycle c = cycle(PerformanceCycleStatus.OPEN);
        when(cycles.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        service.transitionHistory(tenantId, c.getId());

        verify(transitions)
                .findAllByTenantIdAndCycleIdOrderByTransitionedAtAsc(tenantId, c.getId());
    }
}
