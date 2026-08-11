package com.ewos.reimbursement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ewos.employee.domain.Employee;
import com.ewos.employee.infrastructure.persistence.EmployeeRepository;
import com.ewos.reimbursement.api.ReimbursementMapper;
import com.ewos.reimbursement.api.dto.CreateReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.DecideReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.UpdateReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.UploadReimbursementAttachmentRequest;
import com.ewos.reimbursement.domain.ReimbursementCategory;
import com.ewos.reimbursement.domain.ReimbursementClaim;
import com.ewos.reimbursement.domain.ReimbursementClaimAttachment;
import com.ewos.reimbursement.domain.ReimbursementClaimHistory;
import com.ewos.reimbursement.domain.ReimbursementClaimLifecyclePolicy;
import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementCategoryRepository;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementClaimAttachmentRepository;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementClaimHistoryRepository;
import com.ewos.reimbursement.infrastructure.persistence.ReimbursementClaimRepository;
import com.ewos.shared.exception.ApiException;
import com.ewos.tenancy.application.ClientAccessGuard;
import com.ewos.workflow.application.WorkflowDefinitionService;
import com.ewos.workflow.application.WorkflowDelegationService;
import com.ewos.workflow.application.WorkflowInstanceService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ReimbursementClaimServiceTest {

    @Mock ReimbursementClaimRepository claims;
    @Mock ReimbursementCategoryRepository categories;
    @Mock ReimbursementClaimAttachmentRepository attachments;
    @Mock ReimbursementClaimHistoryRepository history;
    @Mock EmployeeRepository employees;
    @Mock ApplicationEventPublisher events;
    @Mock ClientAccessGuard guard;
    @Mock WorkflowInstanceService workflowInstances;
    @Mock WorkflowDefinitionService workflowDefinitions;
    @Mock WorkflowDelegationService delegations;

    private ReimbursementClaimService service;
    private UUID tenantId;
    private UUID companyId;
    private UUID employeeId;
    private UUID managerId;
    private UUID actorUserId;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        service =
                new ReimbursementClaimService(
                        claims,
                        categories,
                        attachments,
                        history,
                        employees,
                        new ReimbursementClaimLifecyclePolicy(),
                        new ReimbursementMapper(),
                        events,
                        guard,
                        workflowInstances,
                        workflowDefinitions,
                        delegations);
        tenantId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        managerId = UUID.randomUUID();
        actorUserId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        lenient().when(claims.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(attachments.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient()
                .when(attachments.findAllByTenantIdAndClaimIdOrderByUploadedAtDesc(any(), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Employee employeeWithManager() {
        Employee manager = new Employee();
        manager.setId(managerId);
        manager.setUserId(actorUserId);

        Employee employee = new Employee();
        employee.setId(employeeId);
        employee.setCompanyId(companyId);
        employee.setManager(manager);
        employee.setUserId(UUID.randomUUID());
        return employee;
    }

    private ReimbursementCategory category() {
        ReimbursementCategory c = new ReimbursementCategory();
        c.setId(categoryId);
        c.setTenantId(tenantId);
        c.setCode("TRAVEL");
        c.setName("Travel");
        return c;
    }

    private ReimbursementClaim draftClaim(Employee employee) {
        ReimbursementClaim c = new ReimbursementClaim();
        c.setId(UUID.randomUUID());
        c.setTenantId(tenantId);
        c.setCompanyId(companyId);
        c.setEmployee(employee);
        c.setClaimNumber("RC-TEST0001");
        c.setCategory(category());
        c.setAmount(new BigDecimal("500.00"));
        c.setCurrency("INR");
        c.setClaimDate(LocalDate.now());
        c.setDescription("Taxi to client site");
        c.setStatus(ReimbursementClaimStatus.DRAFT);
        return c;
    }

    private void authenticateAs(UUID userId, String... authorities) {
        List<SimpleGrantedAuthority> granted =
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId.toString(), null, granted));
    }

    // ------------------------------------------------------------- createDraft

    @Test
    void createDraftPersistsADraftAndRecordsCreateDraftHistory() {
        Employee employee = employeeWithManager();
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));
        when(categories.findByIdAndTenantId(categoryId, tenantId))
                .thenReturn(Optional.of(category()));

        var response =
                service.createDraft(
                        tenantId,
                        employeeId,
                        new CreateReimbursementClaimRequest(
                                categoryId,
                                new BigDecimal("500.00"),
                                "INR",
                                LocalDate.now(),
                                "Taxi"));

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.DRAFT);
        assertThat(response.dataSource().name()).isEqualTo("MANUAL");

        ArgumentCaptor<ReimbursementClaimHistory> captor =
                ArgumentCaptor.forClass(ReimbursementClaimHistory.class);
        verify(history).save(captor.capture());
        assertThat(captor.getValue().getAction().name()).isEqualTo("CREATE_DRAFT");
        assertThat(captor.getValue().getFromStatus()).isNull();
        assertThat(captor.getValue().getToStatus()).isEqualTo(ReimbursementClaimStatus.DRAFT);
    }

    @Test
    void createDraftDefaultsCurrencyWhenNotSupplied() {
        Employee employee = employeeWithManager();
        when(employees.findByIdAndTenantId(employeeId, tenantId)).thenReturn(Optional.of(employee));
        when(categories.findByIdAndTenantId(categoryId, tenantId))
                .thenReturn(Optional.of(category()));

        var response =
                service.createDraft(
                        tenantId,
                        employeeId,
                        new CreateReimbursementClaimRequest(
                                categoryId,
                                new BigDecimal("500.00"),
                                null,
                                LocalDate.now(),
                                "Taxi"));

        assertThat(response.currency()).isNotBlank();
    }

    // ------------------------------------------------------------- updateDraft

    @Test
    void updateDraftOnlyChangesSuppliedFieldsAndRecordsNoHistory() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        var response =
                service.updateDraft(
                        tenantId,
                        employeeId,
                        c.getId(),
                        new UpdateReimbursementClaimRequest(
                                null, new BigDecimal("750.00"), null, null, null));

        assertThat(response.amount()).isEqualByComparingTo("750.00");
        assertThat(response.description()).isEqualTo("Taxi to client site");
        verify(history, never()).save(any());
    }

    @Test
    void updateDraftRejectedOnceSubmitted() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.updateDraft(
                                        tenantId,
                                        employeeId,
                                        c.getId(),
                                        new UpdateReimbursementClaimRequest(
                                                null, BigDecimal.TEN, null, null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.CONFLICT));
    }

    // ---------------------------------------------------------------- submit

    @Test
    void submitTransitionsToSubmittedAndSkipsWorkflowWhenNoneConfigured() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        when(workflowDefinitions.tryFindEffective(tenantId, "REIMBURSEMENT_CLAIM"))
                .thenReturn(Optional.empty());

        var response = service.submit(tenantId, employeeId, c.getId());

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.SUBMITTED);
        assertThat(c.getWorkflowInstanceId()).isNull();
        verify(workflowInstances, never()).start(any(), any());
    }

    @Test
    void submitFromAlreadySubmittedIsRejected() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.submit(tenantId, employeeId, c.getId()))
                .isInstanceOf(ApiException.class);
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancelFromSubmittedAllowed() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        var response = service.cancel(tenantId, employeeId, c.getId());

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.CANCELLED);
    }

    @Test
    void cancelFromManagerApprovedIsRejected() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.MANAGER_APPROVED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.cancel(tenantId, employeeId, c.getId()))
                .isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------- ownership / tenant isolation

    @Test
    void anotherEmployeesClaimIsNotFound() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        UUID differentEmployeeId = UUID.randomUUID();
        assertThatThrownBy(() -> service.getMine(tenantId, differentEmployeeId, c.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void claimNotInThisTenantIsNotFound() {
        when(claims.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(tenantId, employeeId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------------------------ manager decisions

    @Test
    void managerApproveByTheActualManagerSucceeds() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId))
                .thenReturn(List.of(employee.getManager()));
        authenticateAs(actorUserId);

        var response =
                service.approve(tenantId, c.getId(), new DecideReimbursementClaimRequest(null));

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.MANAGER_APPROVED);
        assertThat(c.getManagerDecisionBy()).isEqualTo(actorUserId);
    }

    @Test
    void managerApproveByANonManagerIsForbidden() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        UUID randomActor = UUID.randomUUID();
        when(employees.findAllByUserIdAndTenantId(randomActor, tenantId)).thenReturn(List.of());
        authenticateAs(randomActor);

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId,
                                        c.getId(),
                                        new DecideReimbursementClaimRequest(null)))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void reimbursementAdminBypassesManagerCheck() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        authenticateAs(UUID.randomUUID(), "REIMBURSEMENT_ADMIN");

        var response =
                service.approve(tenantId, c.getId(), new DecideReimbursementClaimRequest(null));

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.MANAGER_APPROVED);
        verify(employees, never()).findAllByUserIdAndTenantId(any(), any());
    }

    @Test
    void managerRejectRequiresANonBlankReason() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.reject(
                                        tenantId,
                                        c.getId(),
                                        new DecideReimbursementClaimRequest(null)))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(claims, never()).save(any());
    }

    @Test
    void managerRejectWithReasonSucceeds() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        when(employees.findAllByUserIdAndTenantId(actorUserId, tenantId))
                .thenReturn(List.of(employee.getManager()));
        authenticateAs(actorUserId);

        var response =
                service.reject(
                        tenantId,
                        c.getId(),
                        new DecideReimbursementClaimRequest("Missing receipt"));

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.MANAGER_REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Missing receipt");
    }

    @Test
    void managerCannotActOnADraftClaim() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.approve(
                                        tenantId,
                                        c.getId(),
                                        new DecideReimbursementClaimRequest(null)))
                .isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------------ finance decisions

    @Test
    void financeApproveByADifferentUserSucceeds() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.MANAGER_APPROVED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        UUID financeActor = UUID.randomUUID();

        var response =
                service.financeApprove(
                        tenantId,
                        c.getId(),
                        financeActor,
                        new DecideReimbursementClaimRequest(null));

        assertThat(response.status()).isEqualTo(ReimbursementClaimStatus.FINANCE_APPROVED);
        assertThat(response.financeDecisionBy()).isEqualTo(financeActor);
    }

    @Test
    void claimSubmitterCanNeverApproveTheirOwnClaimAtFinanceLevel() {
        Employee employee = employeeWithManager();
        UUID submitterUserId = employee.getUserId();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.MANAGER_APPROVED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.financeApprove(
                                        tenantId,
                                        c.getId(),
                                        submitterUserId,
                                        new DecideReimbursementClaimRequest(null)))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void claimSubmitterCanNeverRejectTheirOwnClaimAtFinanceLevel() {
        Employee employee = employeeWithManager();
        UUID submitterUserId = employee.getUserId();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.MANAGER_APPROVED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.financeReject(
                                        tenantId,
                                        c.getId(),
                                        submitterUserId,
                                        new DecideReimbursementClaimRequest("reason")))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void financeRejectRequiresANonBlankReason() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.MANAGER_APPROVED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.financeReject(
                                        tenantId,
                                        c.getId(),
                                        UUID.randomUUID(),
                                        new DecideReimbursementClaimRequest(" ")))
                .isInstanceOf(ApiException.class)
                .satisfies(
                        e ->
                                assertThat(((ApiException) e).getStatus())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void financeCannotActOnASubmittedClaimThatManagerHasNotYetApproved() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.financeApprove(
                                        tenantId,
                                        c.getId(),
                                        UUID.randomUUID(),
                                        new DecideReimbursementClaimRequest(null)))
                .isInstanceOf(ApiException.class);
    }

    // ------------------------------------------------------------------- history

    @Test
    void everyDecisionRecordsAHistoryRowWithActorAndFromToStatus() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.MANAGER_APPROVED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        UUID financeActor = UUID.randomUUID();

        service.financeApprove(
                tenantId, c.getId(), financeActor, new DecideReimbursementClaimRequest(null));

        ArgumentCaptor<ReimbursementClaimHistory> captor =
                ArgumentCaptor.forClass(ReimbursementClaimHistory.class);
        verify(history).save(captor.capture());
        assertThat(captor.getValue().getActorId()).isEqualTo(financeActor);
        assertThat(captor.getValue().getFromStatus())
                .isEqualTo(ReimbursementClaimStatus.MANAGER_APPROVED);
        assertThat(captor.getValue().getToStatus())
                .isEqualTo(ReimbursementClaimStatus.FINANCE_APPROVED);
    }

    // ---------------------------------------------------------------- attachments

    @Test
    void attachmentCanBeAddedWhileSubmitted() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        var response =
                service.addAttachment(
                        tenantId,
                        employeeId,
                        c.getId(),
                        new UploadReimbursementAttachmentRequest(
                                "receipt.jpg",
                                "image/jpeg",
                                1024,
                                "https://storage/receipt.jpg",
                                null));

        assertThat(response.filename()).isEqualTo("receipt.jpg");
    }

    @Test
    void attachmentCannotBeAddedToATerminalClaim() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.FINANCE_APPROVED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.addAttachment(
                                        tenantId,
                                        employeeId,
                                        c.getId(),
                                        new UploadReimbursementAttachmentRequest(
                                                "r.jpg",
                                                "image/jpeg",
                                                1024,
                                                "https://x/r.jpg",
                                                null)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void normalAttachmentUploadCanNeverSetOcrProvenanceFields() {
        // UploadReimbursementAttachmentRequest has no ocrAssisted/ocrRawResponse fields at all —
        // a client cannot falsely mark an attachment as OCR-assisted or inject fabricated OCR
        // output through this path. Only a trusted server-side OCR path may ever do that, and none
        // exists yet.
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        var response =
                service.addAttachment(
                        tenantId,
                        employeeId,
                        c.getId(),
                        new UploadReimbursementAttachmentRequest(
                                "receipt.jpg",
                                "image/jpeg",
                                1024,
                                "https://storage/receipt.jpg",
                                null));

        assertThat(response.ocrAssisted()).isFalse();
        assertThat(c.getDataSource())
                .isEqualTo(com.ewos.reimbursement.domain.ReimbursementDataSource.MANUAL);
    }

    @Test
    void attachmentCanBeDeletedWhileDraft() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        UUID attachmentId = UUID.randomUUID();
        ReimbursementClaimAttachment a = new ReimbursementClaimAttachment();
        a.setId(attachmentId);
        a.setClaim(c);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));
        when(attachments.findByIdAndTenantId(attachmentId, tenantId)).thenReturn(Optional.of(a));

        service.deleteAttachment(tenantId, employeeId, c.getId(), attachmentId);

        verify(attachments).delete(a);
    }

    @Test
    void attachmentCannotBeDeletedOnceSubmitted() {
        Employee employee = employeeWithManager();
        ReimbursementClaim c = draftClaim(employee);
        c.setStatus(ReimbursementClaimStatus.SUBMITTED);
        when(claims.findByIdAndTenantId(c.getId(), tenantId)).thenReturn(Optional.of(c));

        assertThatThrownBy(
                        () ->
                                service.deleteAttachment(
                                        tenantId, employeeId, c.getId(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class);
        verify(attachments, never()).delete(any());
    }
}
