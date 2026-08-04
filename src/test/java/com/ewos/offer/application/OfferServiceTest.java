package com.ewos.offer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.ats.domain.Candidate;
import com.ewos.ats.domain.JobApplication;
import com.ewos.ats.infrastructure.persistence.JobApplicationRepository;
import com.ewos.offer.api.OfferMapper;
import com.ewos.offer.api.dto.AcceptOfferRequest;
import com.ewos.offer.api.dto.CreateOfferRequest;
import com.ewos.offer.api.dto.DeclineOfferRequest;
import com.ewos.offer.api.dto.ExtendOfferRequest;
import com.ewos.offer.api.dto.OfferDecisionRequest;
import com.ewos.offer.api.dto.SubmitOfferRequest;
import com.ewos.offer.api.dto.WithdrawOfferRequest;
import com.ewos.offer.domain.EmploymentType;
import com.ewos.offer.domain.Offer;
import com.ewos.offer.domain.OfferNotifier;
import com.ewos.offer.domain.OfferPolicy;
import com.ewos.offer.domain.OfferStatus;
import com.ewos.offer.domain.events.OfferEvent;
import com.ewos.offer.infrastructure.persistence.OfferNegotiationRepository;
import com.ewos.offer.infrastructure.persistence.OfferRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class OfferServiceTest {

    @Mock OfferRepository offers;
    @Mock OfferNegotiationRepository negotiations;
    @Mock OfferTemplateService templates;
    @Mock JobApplicationRepository applications;
    @Mock OrganizationUnitRepository orgUnits;
    @Mock WorkflowInstanceService workflow;
    @Mock OfferNotifier notifier;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final OfferPolicy policy = new OfferPolicy();
    private final OfferMapper mapper = new OfferMapper();

    private OfferService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new OfferService(
                        offers,
                        negotiations,
                        templates,
                        applications,
                        orgUnits,
                        workflow,
                        policy,
                        notifier,
                        mapper,
                        events,
                        guard);
    }

    private JobApplication applicationWithCandidateAndRequisition() {
        JobApplication a = new JobApplication();
        a.setId(applicationId);
        a.setCompanyId(companyId);
        Candidate c = new Candidate();
        c.setId(UUID.randomUUID());
        a.setCandidate(c);
        JobRequisition r = new JobRequisition();
        r.setId(UUID.randomUUID());
        a.setJobRequisition(r);
        return a;
    }

    private CreateOfferRequest createRequest(BigDecimal base, BigDecimal totalCtc) {
        return new CreateOfferRequest(
                tenantId,
                companyId,
                "OFF-001",
                applicationId,
                null,
                "Engineer",
                null,
                null,
                EmploymentType.FULL_TIME,
                null,
                "USD",
                base,
                null,
                null,
                null,
                null,
                totalCtc,
                null,
                null,
                30,
                90,
                null,
                null);
    }

    private Offer offer(OfferStatus status) {
        Offer o = new Offer();
        o.setId(UUID.randomUUID());
        o.setTenantId(tenantId);
        o.setCompanyId(companyId);
        o.setOfferNumber("OFF-001");
        o.setCurrency("USD");
        o.setBaseSalary(new BigDecimal("100000"));
        o.setTotalCtc(new BigDecimal("100000"));
        o.setStatus(status);
        JobApplication a = applicationWithCandidateAndRequisition();
        o.setApplication(a);
        o.setCandidate(a.getCandidate());
        return o;
    }

    @Test
    void createRejectsDuplicateOfferNumber() {
        when(offers.existsByTenantIdAndCompanyIdAndOfferNumberIgnoreCase(
                        tenantId, companyId, "OFF-001"))
                .thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        createRequest(
                                                new BigDecimal("100000"),
                                                new BigDecimal("100000"))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(offers, never()).save(any());
    }

    @Test
    void createRejectsApplicationMissingCandidateOrRequisition() {
        when(offers.existsByTenantIdAndCompanyIdAndOfferNumberIgnoreCase(
                        tenantId, companyId, "OFF-001"))
                .thenReturn(false);
        JobApplication bare = new JobApplication();
        bare.setId(applicationId);
        bare.setCompanyId(companyId);
        when(applications.findByIdAndTenantId(applicationId, tenantId))
                .thenReturn(Optional.of(bare));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        createRequest(
                                                new BigDecimal("100000"),
                                                new BigDecimal("100000"))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createRejectsIncoherentCompensation() {
        when(offers.existsByTenantIdAndCompanyIdAndOfferNumberIgnoreCase(
                        tenantId, companyId, "OFF-001"))
                .thenReturn(false);
        when(applications.findByIdAndTenantId(applicationId, tenantId))
                .thenReturn(Optional.of(applicationWithCandidateAndRequisition()));

        assertThatThrownBy(
                        () ->
                                service.create(
                                        createRequest(
                                                new BigDecimal("100000"),
                                                new BigDecimal("999999"))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(offers, never()).save(any());
    }

    @Test
    void createSucceedsAsDraft() {
        when(offers.existsByTenantIdAndCompanyIdAndOfferNumberIgnoreCase(
                        tenantId, companyId, "OFF-001"))
                .thenReturn(false);
        when(applications.findByIdAndTenantId(applicationId, tenantId))
                .thenReturn(Optional.of(applicationWithCandidateAndRequisition()));
        when(offers.save(any(Offer.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp =
                service.create(createRequest(new BigDecimal("100000"), new BigDecimal("100000")));

        assertThat(resp.status()).isEqualTo(OfferStatus.DRAFT);
        verify(guard).requireAccessForCompany(companyId);
        verify(events).publishEvent(any(OfferEvent.class));
    }

    @Test
    void submitForApprovalStartsWorkflow() {
        Offer o = offer(OfferStatus.DRAFT);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));
        UUID instanceId = UUID.randomUUID();
        when(workflow.start(any()))
                .thenReturn(
                        new WorkflowInstanceResponse(
                                instanceId,
                                tenantId,
                                companyId,
                                UUID.randomUUID(),
                                "code",
                                1,
                                "offer.approval",
                                o.getId(),
                                null,
                                null,
                                WorkflowInstanceStatus.RUNNING,
                                Instant.now(),
                                null,
                                null,
                                Instant.now(),
                                Instant.now(),
                                null,
                                null,
                                0));

        var resp =
                service.submitForApproval(
                        tenantId, o.getId(), new SubmitOfferRequest(UUID.randomUUID()));

        assertThat(resp.status()).isEqualTo(OfferStatus.PENDING_APPROVAL);
        assertThat(resp.approvalWorkflowInstanceId()).isEqualTo(instanceId);
    }

    @Test
    void approveRequiresPendingApproval() {
        Offer o = offer(OfferStatus.DRAFT);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        assertThatThrownBy(
                        () -> service.approve(tenantId, o.getId(), new OfferDecisionRequest("ok")))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void approveTransitionsToApproved() {
        Offer o = offer(OfferStatus.PENDING_APPROVAL);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        var resp = service.approve(tenantId, o.getId(), new OfferDecisionRequest("Approved"));

        assertThat(resp.status()).isEqualTo(OfferStatus.APPROVED);
    }

    @Test
    void extendRejectsPastExpiry() {
        Offer o = offer(OfferStatus.APPROVED);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        assertThatThrownBy(
                        () ->
                                service.extend(
                                        tenantId,
                                        o.getId(),
                                        new ExtendOfferRequest(
                                                Instant.now().minus(1, ChronoUnit.DAYS))))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void extendSucceedsAndNotifies() {
        Offer o = offer(OfferStatus.APPROVED);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));
        Instant expiry = Instant.now().plus(7, ChronoUnit.DAYS);

        var resp = service.extend(tenantId, o.getId(), new ExtendOfferRequest(expiry));

        assertThat(resp.status()).isEqualTo(OfferStatus.EXTENDED);
        verify(notifier).notifyOfferExtended(o);
    }

    @Test
    void acceptRejectsExpiredOffer() {
        Offer o = offer(OfferStatus.EXTENDED);
        o.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        assertThatThrownBy(
                        () ->
                                service.accept(
                                        tenantId, o.getId(), new AcceptOfferRequest("signature")))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void acceptTransitionsToAcceptedAndNotifies() {
        Offer o = offer(OfferStatus.EXTENDED);
        o.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        var resp = service.accept(tenantId, o.getId(), new AcceptOfferRequest("John Doe"));

        assertThat(resp.status()).isEqualTo(OfferStatus.ACCEPTED);
        verify(notifier).notifyOfferAccepted(o);
    }

    @Test
    void declineTransitionsToDeclinedAndNotifies() {
        Offer o = offer(OfferStatus.EXTENDED);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        var resp =
                service.decline(
                        tenantId, o.getId(), new DeclineOfferRequest("Chose another offer"));

        assertThat(resp.status()).isEqualTo(OfferStatus.DECLINED);
        verify(notifier).notifyOfferDeclined(o);
    }

    @Test
    void withdrawCancelsWorkflowWhenPendingApprovalStillOutstanding() {
        Offer o = offer(OfferStatus.EXTENDED);
        UUID instanceId = UUID.randomUUID();
        o.setApprovalWorkflowInstanceId(instanceId);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        var resp =
                service.withdraw(tenantId, o.getId(), new WithdrawOfferRequest("Position filled"));

        assertThat(resp.status()).isEqualTo(OfferStatus.WITHDRAWN);
        verify(workflow).cancel(tenantId, instanceId, "Position filled");
        verify(notifier).notifyOfferWithdrawn(o);
    }

    @Test
    void markExpiredRejectsWhenNotYetExpired() {
        Offer o = offer(OfferStatus.EXTENDED);
        o.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.markExpired(tenantId, o.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markExpiredTransitionsWhenPastExpiry() {
        Offer o = offer(OfferStatus.EXTENDED);
        o.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        var resp = service.markExpired(tenantId, o.getId());

        assertThat(resp.status()).isEqualTo(OfferStatus.EXPIRED);
        verify(notifier).notifyOfferExpired(o);
    }

    @Test
    void sendReminderRejectsWhenNotExtended() {
        Offer o = offer(OfferStatus.DRAFT);
        when(offers.findByIdAndTenantId(o.getId(), tenantId)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.sendReminder(tenantId, o.getId()))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(offers.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
