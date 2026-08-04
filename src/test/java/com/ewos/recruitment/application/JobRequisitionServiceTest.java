package com.ewos.recruitment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.organization.infrastructure.persistence.OrganizationUnitRepository;
import com.ewos.recruitment.api.RecruitmentMapper;
import com.ewos.recruitment.api.dto.CloseJobRequisitionRequest;
import com.ewos.recruitment.api.dto.CreateJobRequisitionRequest;
import com.ewos.recruitment.api.dto.DecideJobRequisitionRequest;
import com.ewos.recruitment.api.dto.RecordFillRequest;
import com.ewos.recruitment.api.dto.SubmitJobRequisitionRequest;
import com.ewos.recruitment.api.dto.UpdateJobRequisitionRequest;
import com.ewos.recruitment.domain.EmploymentType;
import com.ewos.recruitment.domain.JobPosition;
import com.ewos.recruitment.domain.JobRequisition;
import com.ewos.recruitment.domain.RequisitionPolicy;
import com.ewos.recruitment.domain.RequisitionStatus;
import com.ewos.recruitment.domain.events.RecruitmentEvent;
import com.ewos.recruitment.infrastructure.persistence.JobPositionRepository;
import com.ewos.recruitment.infrastructure.persistence.JobRequisitionRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.api.dto.WorkflowInstanceResponse;
import com.ewos.workflow.application.WorkflowInstanceService;
import com.ewos.workflow.domain.WorkflowInstanceStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class JobRequisitionServiceTest {

    @Mock JobRequisitionRepository requisitions;
    @Mock JobPositionRepository positions;
    @Mock EmployeeRepository employees;
    @Mock OrganizationUnitRepository orgUnits;
    @Mock WorkflowInstanceService workflow;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;

    private final RequisitionPolicy policy = new RequisitionPolicy();
    private final RecruitmentMapper mapper = new RecruitmentMapper();

    private JobRequisitionService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service =
                new JobRequisitionService(
                        requisitions,
                        positions,
                        employees,
                        orgUnits,
                        policy,
                        workflow,
                        mapper,
                        events,
                        guard);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID actorId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(actorId.toString(), null, null));
    }

    private JobPosition activePosition(UUID id) {
        JobPosition p = new JobPosition();
        p.setId(id);
        p.setTenantId(tenantId);
        p.setCompanyId(companyId);
        p.setActive(true);
        return p;
    }

    private CreateJobRequisitionRequest createRequest(UUID positionId) {
        return new CreateJobRequisitionRequest(
                tenantId,
                companyId,
                "REQ-001",
                positionId,
                "Senior Engineer",
                null,
                "Remote",
                EmploymentType.FULL_TIME,
                2,
                null,
                "Backfill",
                null,
                null,
                null,
                null,
                null);
    }

    private JobRequisition requisition(RequisitionStatus status) {
        JobRequisition r = new JobRequisition();
        r.setId(UUID.randomUUID());
        r.setTenantId(tenantId);
        r.setCompanyId(companyId);
        r.setRequisitionNumber("REQ-001");
        r.setHeadcount(2);
        r.setJustification("Backfill");
        r.setStatus(status);
        return r;
    }

    @Test
    void createRejectsDuplicateRequisitionNumber() {
        UUID positionId = UUID.randomUUID();
        when(requisitions.existsByTenantIdAndCompanyIdAndRequisitionNumberIgnoreCase(
                        tenantId, companyId, "REQ-001"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(createRequest(positionId)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(requisitions, never()).save(any());
    }

    @Test
    void createRejectsPositionFromDifferentCompany() {
        UUID positionId = UUID.randomUUID();
        JobPosition otherCompanyPosition = activePosition(positionId);
        otherCompanyPosition.setCompanyId(UUID.randomUUID());
        when(requisitions.existsByTenantIdAndCompanyIdAndRequisitionNumberIgnoreCase(
                        tenantId, companyId, "REQ-001"))
                .thenReturn(false);
        when(positions.findByIdAndTenantId(positionId, tenantId))
                .thenReturn(Optional.of(otherCompanyPosition));

        assertThatThrownBy(() -> service.create(createRequest(positionId)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsInactivePosition() {
        UUID positionId = UUID.randomUUID();
        JobPosition inactive = activePosition(positionId);
        inactive.setActive(false);
        when(requisitions.existsByTenantIdAndCompanyIdAndRequisitionNumberIgnoreCase(
                        tenantId, companyId, "REQ-001"))
                .thenReturn(false);
        when(positions.findByIdAndTenantId(positionId, tenantId)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(createRequest(positionId)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createSucceedsAndPublishesEvent() {
        UUID positionId = UUID.randomUUID();
        when(requisitions.existsByTenantIdAndCompanyIdAndRequisitionNumberIgnoreCase(
                        tenantId, companyId, "REQ-001"))
                .thenReturn(false);
        when(positions.findByIdAndTenantId(positionId, tenantId))
                .thenReturn(Optional.of(activePosition(positionId)));
        when(requisitions.save(any(JobRequisition.class))).thenAnswer(inv -> inv.getArgument(0));

        var resp = service.create(createRequest(positionId));

        assertThat(resp.status()).isEqualTo(RequisitionStatus.DRAFT);
        verify(guard).requireAccessForCompany(companyId);
        verify(events).publishEvent(any(RecruitmentEvent.class));
    }

    @Test
    void updateRejectsHeadcountBelowFilledCount() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        r.setFilledCount(3);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var req =
                new UpdateJobRequisitionRequest(
                        "Title",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        1,
                        null,
                        "J",
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> service.update(tenantId, r.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateRejectsWhenNotDraft() {
        JobRequisition r = requisition(RequisitionStatus.OPEN);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var req =
                new UpdateJobRequisitionRequest(
                        "Title",
                        null,
                        null,
                        EmploymentType.FULL_TIME,
                        2,
                        null,
                        "J",
                        null,
                        null,
                        null,
                        null,
                        null);

        assertThatThrownBy(() -> service.update(tenantId, r.getId(), req))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void submitStartsWorkflowAndTransitionsToPendingApproval() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
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
                                "recruitment.requisition",
                                r.getId(),
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
                service.submit(
                        tenantId, r.getId(), new SubmitJobRequisitionRequest(UUID.randomUUID()));

        assertThat(resp.status()).isEqualTo(RequisitionStatus.PENDING_APPROVAL);
        assertThat(resp.workflowInstanceId()).isEqualTo(instanceId);
    }

    @Test
    void submitRejectsWhenMissingJustification() {
        JobRequisition r = requisition(RequisitionStatus.DRAFT);
        r.setJustification(null);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.submit(
                                        tenantId,
                                        r.getId(),
                                        new SubmitJobRequisitionRequest(UUID.randomUUID())))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void approveRequiresAuthenticatedActor() {
        JobRequisition r = requisition(RequisitionStatus.PENDING_APPROVAL);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId, r.getId(), new DecideJobRequisitionRequest("ok")))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void approveTransitionsToApprovedAndRecordsActor() {
        JobRequisition r = requisition(RequisitionStatus.PENDING_APPROVAL);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        UUID actor = UUID.randomUUID();
        authenticateAs(actor);

        var resp =
                service.approve(tenantId, r.getId(), new DecideJobRequisitionRequest("Approved"));

        assertThat(resp.status()).isEqualTo(RequisitionStatus.APPROVED);
        assertThat(resp.decidedBy()).isEqualTo(actor);
    }

    @Test
    void rejectTransitionsToRejected() {
        JobRequisition r = requisition(RequisitionStatus.PENDING_APPROVAL);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));
        authenticateAs(UUID.randomUUID());

        var resp =
                service.reject(tenantId, r.getId(), new DecideJobRequisitionRequest("No budget"));

        assertThat(resp.status()).isEqualTo(RequisitionStatus.REJECTED);
    }

    @Test
    void recordFillRejectsWhenExceedingHeadcount() {
        JobRequisition r = requisition(RequisitionStatus.OPEN);
        r.setHeadcount(1);
        r.setFilledCount(0);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.recordFill(tenantId, r.getId(), new RecordFillRequest(2)))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recordFillClosesRequisitionWhenHeadcountReached() {
        JobRequisition r = requisition(RequisitionStatus.OPEN);
        r.setHeadcount(1);
        r.setFilledCount(0);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var resp = service.recordFill(tenantId, r.getId(), new RecordFillRequest(1));

        assertThat(resp.status()).isEqualTo(RequisitionStatus.FILLED);
    }

    @Test
    void cancelCancelsWorkflowInstanceWhenPendingApproval() {
        JobRequisition r = requisition(RequisitionStatus.PENDING_APPROVAL);
        UUID instanceId = UUID.randomUUID();
        r.setWorkflowInstanceId(instanceId);
        when(requisitions.findByIdAndTenantId(r.getId(), tenantId)).thenReturn(Optional.of(r));

        var resp =
                service.cancel(tenantId, r.getId(), new CloseJobRequisitionRequest("Budget cut"));

        assertThat(resp.status()).isEqualTo(RequisitionStatus.CANCELLED);
        verify(workflow).cancel(tenantId, instanceId, "Budget cut");
    }

    @Test
    void getByIdThrowsNotFoundWhenMissing() {
        UUID id = UUID.randomUUID();
        when(requisitions.findByIdAndTenantId(id, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(tenantId, id))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
