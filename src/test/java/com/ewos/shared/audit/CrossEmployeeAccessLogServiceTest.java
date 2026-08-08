package com.ewos.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CrossEmployeeAccessLogServiceTest {

    @Mock CrossEmployeeAccessLogRepository repository;

    private CrossEmployeeAccessLogService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CrossEmployeeAccessLogService(repository);
    }

    @Test
    void logGrantedPersistsAGrantedEntryWithNoReason() {
        ArgumentCaptor<CrossEmployeeAccessLog> captor =
                ArgumentCaptor.forClass(CrossEmployeeAccessLog.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.logGranted(tenantId, actorId, targetId, "TEAM_DRILL_DOWN");

        CrossEmployeeAccessLog saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(tenantId);
        assertThat(saved.getActorEmployeeId()).isEqualTo(actorId);
        assertThat(saved.getTargetEmployeeId()).isEqualTo(targetId);
        assertThat(saved.getAction()).isEqualTo("TEAM_DRILL_DOWN");
        assertThat(saved.isGranted()).isTrue();
        assertThat(saved.getReason()).isNull();
        assertThat(saved.getOccurredAt()).isNotNull();
    }

    @Test
    void logDeniedPersistsADeniedEntryWithTheReason() {
        ArgumentCaptor<CrossEmployeeAccessLog> captor =
                ArgumentCaptor.forClass(CrossEmployeeAccessLog.class);
        when(repository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        service.logDenied(tenantId, actorId, targetId, "TEAM_DRILL_DOWN", "not a direct report");

        CrossEmployeeAccessLog saved = captor.getValue();
        assertThat(saved.isGranted()).isFalse();
        assertThat(saved.getReason()).isEqualTo("not a direct report");
    }

    @Test
    void historyForTargetDelegatesToTheRepository() {
        CrossEmployeeAccessLog entry = new CrossEmployeeAccessLog();
        when(repository.findAllByTenantIdAndTargetEmployeeIdOrderByOccurredAtDesc(
                        tenantId, targetId))
                .thenReturn(List.of(entry));

        assertThat(service.historyForTarget(tenantId, targetId)).containsExactly(entry);
    }

    @Test
    void historyForActorDelegatesToTheRepository() {
        CrossEmployeeAccessLog entry = new CrossEmployeeAccessLog();
        when(repository.findAllByTenantIdAndActorEmployeeIdOrderByOccurredAtDesc(tenantId, actorId))
                .thenReturn(List.of(entry));

        assertThat(service.historyForActor(tenantId, actorId)).containsExactly(entry);
    }

    @Test
    void logAlwaysSavesExactlyOnce() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.log(tenantId, actorId, targetId, "APPROVAL_ACTION", true, null);

        verify(repository).save(any());
    }
}
