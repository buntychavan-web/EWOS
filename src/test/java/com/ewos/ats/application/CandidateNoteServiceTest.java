package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.AddCandidateNoteRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateNote;
import com.ewos.ats.domain.NoteType;
import com.ewos.ats.domain.TimelineEventType;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.infrastructure.persistence.CandidateNoteRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CandidateNoteServiceTest {

    @Mock CandidateNoteRepository notes;
    @Mock CandidateService candidates;
    @Mock CandidateTimelineService timeline;
    @Mock ApplicationEventPublisher events;

    private final AtsMapper mapper = new AtsMapper();

    private CandidateNoteService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CandidateNoteService(notes, candidates, timeline, mapper, events);
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);
        c.setCompanyId(UUID.randomUUID());
        return c;
    }

    @Test
    void addNoteDefaultsPrivateNoteToFalseWhenNull() {
        Candidate c = candidate();
        when(candidates.require(tenantId, candidateId)).thenReturn(c);
        when(notes.save(any(CandidateNote.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp =
                service.addNote(
                        tenantId,
                        candidateId,
                        new AddCandidateNoteRequest(NoteType.GENERAL, "Body", null));

        assertThat(resp.privateNote()).isFalse();
        verify(timeline)
                .record(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.eq(TimelineEventType.NOTE_ADDED),
                        any(),
                        any());
        verify(events).publishEvent(any(AtsEvent.class));
    }

    @Test
    void addNoteHonorsPrivateFlagWhenTrue() {
        Candidate c = candidate();
        when(candidates.require(tenantId, candidateId)).thenReturn(c);
        when(notes.save(any(CandidateNote.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp =
                service.addNote(
                        tenantId,
                        candidateId,
                        new AddCandidateNoteRequest(NoteType.HR, "Confidential", true));

        assertThat(resp.privateNote()).isTrue();
    }
}
