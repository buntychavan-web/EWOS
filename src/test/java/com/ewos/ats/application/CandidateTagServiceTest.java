package com.ewos.ats.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.api.AtsMapper;
import com.ewos.ats.api.dto.AddCandidateTagRequest;
import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.CandidateTag;
import com.ewos.ats.domain.events.AtsEvent;
import com.ewos.ats.infrastructure.persistence.CandidateTagRepository;
import com.ewos.shared.exception.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CandidateTagServiceTest {

    @Mock CandidateTagRepository tags;
    @Mock CandidateService candidates;
    @Mock CandidateTimelineService timeline;
    @Mock ApplicationEventPublisher events;

    private final AtsMapper mapper = new AtsMapper();

    private CandidateTagService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID candidateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CandidateTagService(tags, candidates, timeline, mapper, events);
    }

    private Candidate candidate() {
        Candidate c = new Candidate();
        c.setId(candidateId);
        c.setTenantId(tenantId);
        c.setCompanyId(UUID.randomUUID());
        return c;
    }

    @Test
    void addTagRejectsDuplicateCaseInsensitive() {
        when(candidates.require(tenantId, candidateId)).thenReturn(candidate());
        when(tags.existsByTenantIdAndCandidateIdAndTagIgnoreCase(tenantId, candidateId, "Senior"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.addTag(
                                        tenantId,
                                        candidateId,
                                        new AddCandidateTagRequest("Senior")))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(tags, never()).save(any());
    }

    @Test
    void addTagTrimsWhitespaceBeforeSaving() {
        when(candidates.require(tenantId, candidateId)).thenReturn(candidate());
        when(tags.existsByTenantIdAndCandidateIdAndTagIgnoreCase(tenantId, candidateId, "Senior"))
                .thenReturn(false);
        when(tags.save(any(CandidateTag.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.addTag(tenantId, candidateId, new AddCandidateTagRequest("  Senior  "));

        assertThat(resp.tag()).isEqualTo("Senior");
        verify(events).publishEvent(any(AtsEvent.class));
    }

    @Test
    void removeTagThrowsNotFoundWhenAbsent() {
        when(candidates.require(tenantId, candidateId)).thenReturn(candidate());
        when(tags.findByTenantIdAndCandidateIdAndTagIgnoreCase(tenantId, candidateId, "Ghost"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeTag(tenantId, candidateId, "Ghost"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void removeTagDeletesAndPublishesEvent() {
        when(candidates.require(tenantId, candidateId)).thenReturn(candidate());
        CandidateTag existing = new CandidateTag();
        existing.setTag("Senior");
        when(tags.findByTenantIdAndCandidateIdAndTagIgnoreCase(tenantId, candidateId, "Senior"))
                .thenReturn(Optional.of(existing));

        service.removeTag(tenantId, candidateId, "Senior");

        verify(tags).delete(existing);
        verify(events).publishEvent(any(AtsEvent.class));
    }
}
