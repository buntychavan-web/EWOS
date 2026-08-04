package com.ewos.offer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.ConfirmJoiningRequest;
import com.ewos.offer.api.dto.UpdateTaskStatusRequest;
import com.ewos.offer.domain.BackgroundVerificationService;
import com.ewos.offer.domain.EmployeeIdGenerator;
import com.ewos.offer.domain.MedicalCheckService;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferNotifier;
import com.ewos.offer.domain.OfferStatus;
import com.ewos.offer.domain.ReferenceCheckService;
import com.ewos.offer.domain.preboarding.PreboardingChecklist;
import com.ewos.offer.domain.preboarding.PreboardingChecklistStatus;
import com.ewos.offer.domain.preboarding.PreboardingTaskInstance;
import com.ewos.offer.domain.preboarding.PreboardingTaskOwner;
import com.ewos.offer.domain.preboarding.PreboardingTaskStatus;
import com.ewos.offer.domain.preboarding.PreboardingTaskTemplate;
import com.ewos.offer.domain.preboarding.PreboardingTaskType;
import com.ewos.offer.infrastructure.persistence.OfferRepository;
import com.ewos.offer.infrastructure.persistence.PreboardingChecklistRepository;
import com.ewos.offer.infrastructure.persistence.PreboardingTaskInstanceRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import java.time.LocalDate;
import java.util.List;
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
class PreboardingServiceTest {

    @Mock PreboardingChecklistRepository checklists;
    @Mock PreboardingTaskInstanceRepository tasks;
    @Mock PreboardingTaskTemplateService templates;
    @Mock OfferRepository offers;
    @Mock EmployeeRepository employees;
    @Mock BackgroundVerificationService bgv;
    @Mock MedicalCheckService medical;
    @Mock ReferenceCheckService referenceCheck;
    @Mock EmployeeIdGenerator employeeIdGenerator;
    @Mock OfferNotifier notifier;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final OfferMapper mapper = new OfferMapper();

    private PreboardingService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new PreboardingService(
                        checklists,
                        tasks,
                        templates,
                        offers,
                        employees,
                        bgv,
                        medical,
                        referenceCheck,
                        employeeIdGenerator,
                        notifier,
                        mapper,
                        events,
                        guard);
    }

    private Offer offer(OfferStatus status) {
        Offer o = new Offer();
        o.setId(UUID.randomUUID());
        o.setTenantId(tenantId);
        o.setCompanyId(companyId);
        o.setOfferNumber("OFF-001");
        o.setStatus(status);
        o.setTargetJoiningDate(LocalDate.now().plusDays(30));
        return o;
    }

    private PreboardingTaskTemplate manualTemplate() {
        PreboardingTaskTemplate t = new PreboardingTaskTemplate();
        t.setId(UUID.randomUUID());
        t.setName("Sign NDA");
        t.setTaskType(PreboardingTaskType.DOCUMENT_COLLECTION);
        t.setDefaultOwner(PreboardingTaskOwner.HR);
        t.setMandatory(true);
        t.setSortOrder(1);
        return t;
    }

    private PreboardingChecklist checklist(PreboardingChecklistStatus status) {
        PreboardingChecklist c = new PreboardingChecklist();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        c.setStatus(status);
        c.setOffer(offer(OfferStatus.ACCEPTED));
        return c;
    }

    @Test
    void createChecklistRejectsOfferNotAccepted() {
        Offer o = offer(OfferStatus.EXTENDED);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.createChecklistForOffer(tenantId, o.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createChecklistIsIdempotent() {
        Offer o = offer(OfferStatus.ACCEPTED);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));
        PreboardingChecklist existing = checklist(PreboardingChecklistStatus.PENDING);
        when(checklists.findByTenantIdAndOfferId(tenantId, o.getId()))
                .thenReturn(Optional.of(existing));

        var resp = service.createChecklistForOffer(tenantId, o.getId());

        assertThat(resp.id()).isEqualTo(existing.getId());
        verify(checklists, never()).save(any());
    }

    @Test
    void createChecklistSpawnsTasksFromActiveTemplates() {
        Offer o = offer(OfferStatus.ACCEPTED);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));
        when(checklists.findByTenantIdAndOfferId(tenantId, o.getId())).thenReturn(Optional.empty());
        when(checklists.save(any(PreboardingChecklist.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(templates.activeTemplatesFor(tenantId, companyId))
                .thenReturn(List.of(manualTemplate()));
        when(tasks.findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(any(), any()))
                .thenReturn(List.of());

        var resp = service.createChecklistForOffer(tenantId, o.getId());

        assertThat(resp.status()).isEqualTo(PreboardingChecklistStatus.PENDING);
        verify(tasks).save(any(PreboardingTaskInstance.class));
        verify(guard).requireAccessForCompany(companyId);
    }

    @Test
    void updateTaskStatusRejectsAlreadyTerminalTask() {
        PreboardingChecklist c = checklist(PreboardingChecklistStatus.IN_PROGRESS);
        PreboardingTaskInstance t = new PreboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setChecklist(c);
        t.setTaskType(PreboardingTaskType.DOCUMENT_COLLECTION);
        t.setStatus(PreboardingTaskStatus.COMPLETED);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        assertThatThrownBy(
                        () ->
                                service.updateTaskStatus(
                                        tenantId,
                                        t.getId(),
                                        new UpdateTaskStatusRequest(
                                                PreboardingTaskStatus.SKIPPED, null, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateTaskStatusTransitionsAndRecomputesCompletion() {
        PreboardingChecklist c = checklist(PreboardingChecklistStatus.PENDING);
        PreboardingTaskInstance t = new PreboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setChecklist(c);
        t.setTaskType(PreboardingTaskType.DOCUMENT_COLLECTION);
        t.setStatus(PreboardingTaskStatus.PENDING);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));
        when(tasks.findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(tenantId, c.getId()))
                .thenReturn(List.of(t));

        var resp =
                service.updateTaskStatus(
                        tenantId,
                        t.getId(),
                        new UpdateTaskStatusRequest(PreboardingTaskStatus.COMPLETED, "done", null));

        assertThat(resp.status()).isEqualTo(PreboardingTaskStatus.COMPLETED);
        assertThat(c.getCompletionPercent()).isEqualByComparingTo("100.00");
        assertThat(c.getStatus()).isEqualTo(PreboardingChecklistStatus.COMPLETED);
    }

    @Test
    void confirmJoiningRejectsOutstandingMandatoryTasks() {
        PreboardingChecklist c = checklist(PreboardingChecklistStatus.IN_PROGRESS);
        when(checklists.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        PreboardingTaskInstance outstanding = new PreboardingTaskInstance();
        outstanding.setMandatory(true);
        outstanding.setStatus(PreboardingTaskStatus.PENDING);
        when(tasks.findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(tenantId, c.getId()))
                .thenReturn(List.of(outstanding));

        assertThatThrownBy(
                        () ->
                                service.confirmJoining(
                                        tenantId, c.getId(), new ConfirmJoiningRequest(null, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void confirmJoiningSucceedsAndNotifies() {
        PreboardingChecklist c = checklist(PreboardingChecklistStatus.COMPLETED);
        when(checklists.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        when(tasks.findAllByTenantIdAndChecklistIdOrderBySortOrderAsc(tenantId, c.getId()))
                .thenReturn(List.of());

        var resp =
                service.confirmJoining(
                        tenantId, c.getId(), new ConfirmJoiningRequest(null, "Welcome"));

        assertThat(resp.status()).isEqualTo(PreboardingChecklistStatus.JOINED);
        verify(notifier).notifyCandidateJoined(c.getOffer());
    }

    @Test
    void markNoShowRejectsAlreadyJoinedChecklist() {
        PreboardingChecklist c = checklist(PreboardingChecklistStatus.JOINED);
        when(checklists.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.markNoShow(tenantId, c.getId(), "Never showed"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelRejectsAlreadyJoinedChecklist() {
        PreboardingChecklist c = checklist(PreboardingChecklistStatus.JOINED);
        when(checklists.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.cancel(tenantId, c.getId(), "Offer rescinded"))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void sendTaskReminderRejectsTerminalTask() {
        PreboardingChecklist c = checklist(PreboardingChecklistStatus.IN_PROGRESS);
        PreboardingTaskInstance t = new PreboardingTaskInstance();
        t.setId(UUID.randomUUID());
        t.setChecklist(c);
        t.setStatus(PreboardingTaskStatus.COMPLETED);
        when(tasks.findByIdAndTenantId(t.getId(), tenantId)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.sendTaskReminder(tenantId, t.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(checklists.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
