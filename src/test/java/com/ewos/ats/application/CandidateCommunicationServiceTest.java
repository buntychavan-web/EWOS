package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.LogCommunicationRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateCommunication;
import com.ewos.ats.domain.CommunicationChannel;
import com.ewos.ats.domain.CommunicationDirection;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.infrastructure.persistence.CandidateCommunicationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CandidateCommunicationServiceTest {

    @Mock CandidateCommunicationRepository communications;
    @Mock CandidateService candidates;
    @Mock CandidateTimelineService timeline;
    @Mock ApplicationEventPublisher events;

    private final AtsMapper mapper = new AtsMapper();

    private CandidateCommunicationService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new CandidateCommunicationService(
                        communications, candidates, timeline, mapper, events);
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);
        c.setCompanyId(UUID.randomUUID());
        return c;
    }

    @Test
    void logRecordsTimelineEntryAndPublishesEvent() {
        Candidate c = candidate();
        when(candidates.require(tenantId, candidateId)).thenReturn(c);
        when(communications.save(any(CandidateCommunication.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var req =
                new LogCommunicationRequest(
                        CommunicationChannel.EMAIL,
                        CommunicationDirection.OUTBOUND,
                        null,
                        "Interview invite",
                        "Sent interview invite",
                        null,
                        null);

        var resp = service.log(tenantId, candidateId, req);

        assertThat(resp.channel()).isEqualTo(CommunicationChannel.EMAIL);
        assertThat(resp.direction()).isEqualTo(CommunicationDirection.OUTBOUND);
        verify(timeline)
                .record(
                        eq(c),
                        eq(req.applicationId()),
                        eq(TimelineEventType.COMMUNICATION_LOGGED),
                        any(),
                        eq(req.subject()));
        verify(events).publishEvent(any(AtsEvent.class));
    }

    @Test
    void listForCandidateChecksCandidateAccessFirst() {
        when(candidates.require(tenantId, candidateId)).thenReturn(candidate());
        when(communications.findAllByTenantIdAndCandidateIdOrderByOccurredAtDesc(
                        tenantId, candidateId))
                .thenReturn(List.of());

        var result = service.listForCandidate(tenantId, candidateId);

        assertThat(result).isEmpty();
        verify(candidates).require(tenantId, candidateId);
    }
}
