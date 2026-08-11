package com.ewos.reimbursement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.employee.domain.ApprovalAction;
import com.ewos.reimbursement.api.dto.BulkReimbursementDecisionItemRequest;
import com.ewos.reimbursement.api.dto.BulkReimbursementDecisionRequest;
import com.ewos.reimbursement.api.dto.BulkReimbursementDecisionResponse;
import com.ewos.reimbursement.api.dto.DecideReimbursementClaimRequest;
import com.ewos.reimbursement.api.dto.ReimbursementClaimResponse;
import com.ewos.reimbursement.application.ReimbursementClaimService;
import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import com.ewos.reimbursement.domain.ReimbursementDataSource;
import com.ewos.shared.exception.ApiException;
import com.ewos.shared.exception.GlobalExceptionHandler;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Mirrors {@code ReimbursementSelfServiceControllerTest}'s standalone-MockMvc approach: real
 * controller + real {@code GlobalExceptionHandler} against mocked collaborators.
 *
 * <p>Note on permission enforcement: like every other controller test in this codebase (see {@code
 * EssProfileControllerTest}'s own documented limitation), standalone {@code MockMvcBuilders} does
 * not wire Spring Security's method-security interceptor, so {@code @PreAuthorize} is not actually
 * evaluated here — {@link #everyFinanceEndpointRequiresReimbursementFinanceApprove()} instead
 * asserts, by reflection, that the exact expected annotation is present on every finance endpoint.
 * End-to-end enforcement is exercised only through the real filter chain, which needs the full
 * Spring context (Docker/Testcontainers) this sandbox does not have.
 */
@ExtendWith(MockitoExtension.class)
class ReimbursementFinanceControllerTest {

    private static final String BASE = "/api/v1/reimbursements";
    private static final String HEADER = "Idempotency-Key";
    private static final String REQUIRED_AUTHORITY = "REIMBURSEMENT_FINANCE_APPROVE";

    @Mock ReimbursementClaimService claims;
    @Mock TenantContext tenantContext;
    @Mock IdempotencyService idempotency;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID claimId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReimbursementFinanceController controller =
                new ReimbursementFinanceController(claims, tenantContext, idempotency);
        mockMvc =
                MockMvcBuilders.standaloneSetup(controller)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @SuppressWarnings("unchecked")
    private void stubIdempotencyToRunTheAction() {
        when(idempotency.execute(
                        any(), any(), any(), any(), eq(ReimbursementClaimResponse.class), any()))
                .thenAnswer(
                        inv -> ((Supplier<ReimbursementClaimResponse>) inv.getArgument(5)).get());
    }

    @SuppressWarnings("unchecked")
    private void stubIdempotencyToRunBulkAction() {
        when(idempotency.execute(
                        any(),
                        any(),
                        any(),
                        any(),
                        eq(BulkReimbursementDecisionResponse.class),
                        any()))
                .thenAnswer(
                        inv ->
                                ((Supplier<BulkReimbursementDecisionResponse>) inv.getArgument(5))
                                        .get());
    }

    private ReimbursementClaimResponse response(ReimbursementClaimStatus status) {
        return new ReimbursementClaimResponse(
                claimId,
                tenantId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "RC-TEST0001",
                UUID.randomUUID(),
                "TRAVEL",
                "Travel",
                new java.math.BigDecimal("500.00"),
                "INR",
                LocalDate.now(),
                "Taxi",
                status,
                ReimbursementDataSource.MANUAL,
                Instant.now(),
                Instant.now(),
                UUID.randomUUID(),
                status == ReimbursementClaimStatus.FINANCE_APPROVED
                                || status == ReimbursementClaimStatus.FINANCE_REJECTED
                        ? Instant.now()
                        : null,
                status == ReimbursementClaimStatus.FINANCE_APPROVED
                                || status == ReimbursementClaimStatus.FINANCE_REJECTED
                        ? actorId
                        : null,
                null,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                0L);
    }

    // ------------------------------------------------------------ permission enforcement

    @Test
    void everyFinanceEndpointRequiresReimbursementFinanceApprove() throws NoSuchMethodException {
        assertHasRequiredAuthority(
                ReimbursementFinanceController.class.getMethod(
                        "pending", UUID.class, org.springframework.data.domain.Pageable.class));
        assertHasRequiredAuthority(
                ReimbursementFinanceController.class.getMethod(
                        "approve",
                        UUID.class,
                        String.class,
                        DecideReimbursementClaimRequest.class));
        assertHasRequiredAuthority(
                ReimbursementFinanceController.class.getMethod(
                        "reject", UUID.class, String.class, DecideReimbursementClaimRequest.class));
        assertHasRequiredAuthority(
                ReimbursementFinanceController.class.getMethod(
                        "bulkAct", String.class, BulkReimbursementDecisionRequest.class));
    }

    private static void assertHasRequiredAuthority(Method method) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("method %s must be @PreAuthorize-guarded", method.getName())
                .isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAuthority('" + REQUIRED_AUTHORITY + "')");
    }

    // ------------------------------------------------------------ idempotency

    @Test
    void approveReturns400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        mockMvc.perform(
                        post(BASE + "/" + claimId + "/finance/approve")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DecideReimbursementClaimRequest(null))))
                .andExpect(status().isBadRequest());

        verify(claims, never()).financeApprove(any(), any(), any(), any());
    }

    @Test
    void rejectReturns400WhenIdempotencyKeyHeaderIsBlank() throws Exception {
        mockMvc.perform(
                        post(BASE + "/" + claimId + "/finance/reject")
                                .header(HEADER, "   ")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DecideReimbursementClaimRequest("reason"))))
                .andExpect(status().isBadRequest());

        verify(claims, never()).financeReject(any(), any(), any(), any());
    }

    @Test
    void bulkActReturns400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        BulkReimbursementDecisionRequest request =
                new BulkReimbursementDecisionRequest(
                        List.of(
                                new BulkReimbursementDecisionItemRequest(
                                        claimId, ApprovalAction.APPROVE, null)));

        mockMvc.perform(
                        post(BASE + "/finance/bulk-act")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(claims, never()).financeApprove(any(), any(), any(), any());
    }

    // ------------------------------------------------------------ self-approval / reason

    @Test
    void approveReturns403WhenTheSubmitterTriesToApproveTheirOwnClaim() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        when(claims.financeApprove(eq(tenantId), eq(claimId), eq(actorId), any()))
                .thenThrow(
                        new ApiException(HttpStatus.FORBIDDEN, "You cannot decide your own claim"));

        mockMvc.perform(
                        post(BASE + "/" + claimId + "/finance/approve")
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DecideReimbursementClaimRequest(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectReturns403WhenTheSubmitterTriesToRejectTheirOwnClaim() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        when(claims.financeReject(eq(tenantId), eq(claimId), eq(actorId), any()))
                .thenThrow(
                        new ApiException(HttpStatus.FORBIDDEN, "You cannot decide your own claim"));

        mockMvc.perform(
                        post(BASE + "/" + claimId + "/finance/reject")
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DecideReimbursementClaimRequest("reason"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectReturns400WhenTheServiceReportsAMissingReason() throws Exception {
        // DecideReimbursementClaimRequest has no @NotBlank on `reason` (mirrors
        // DecideLeaveRequestRequest) — the requirement is enforced in the service, which the
        // controller propagates as-is.
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        when(claims.financeReject(eq(tenantId), eq(claimId), eq(actorId), any()))
                .thenThrow(
                        new ApiException(HttpStatus.BAD_REQUEST, "A rejection reason is required"));

        mockMvc.perform(
                        post(BASE + "/" + claimId + "/finance/reject")
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DecideReimbursementClaimRequest(null))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ success paths

    @Test
    void approveSucceedsAndScopesToTheResolvedTenant() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        when(claims.financeApprove(eq(tenantId), eq(claimId), eq(actorId), any()))
                .thenReturn(response(ReimbursementClaimStatus.FINANCE_APPROVED));

        mockMvc.perform(
                        post(BASE + "/" + claimId + "/finance/approve")
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DecideReimbursementClaimRequest(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINANCE_APPROVED"));

        verify(claims).financeApprove(eq(tenantId), eq(claimId), eq(actorId), any());
    }

    @Test
    void rejectSucceedsWithAReason() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        when(claims.financeReject(eq(tenantId), eq(claimId), eq(actorId), any()))
                .thenReturn(response(ReimbursementClaimStatus.FINANCE_REJECTED));

        mockMvc.perform(
                        post(BASE + "/" + claimId + "/finance/reject")
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                new DecideReimbursementClaimRequest(
                                                        "Missing receipt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINANCE_REJECTED"));
    }

    // ------------------------------------------------------------ bulk-act

    @Test
    void bulkActReportsIndependentSuccessAndFailurePerItem() throws Exception {
        UUID okClaimId = UUID.randomUUID();
        UUID badClaimId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunBulkAction();
        when(claims.financeApprove(eq(tenantId), eq(okClaimId), eq(actorId), any()))
                .thenReturn(response(ReimbursementClaimStatus.FINANCE_APPROVED));
        when(claims.financeReject(eq(tenantId), eq(badClaimId), eq(actorId), any()))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Reimbursement claim not found"));

        BulkReimbursementDecisionRequest request =
                new BulkReimbursementDecisionRequest(
                        List.of(
                                new BulkReimbursementDecisionItemRequest(
                                        okClaimId, ApprovalAction.APPROVE, null),
                                new BulkReimbursementDecisionItemRequest(
                                        badClaimId, ApprovalAction.REJECT, "reason")));

        mockMvc.perform(
                        post(BASE + "/finance/bulk-act")
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1))
                .andExpect(jsonPath("$.results[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.results[1].status").value("FAILED"))
                // Enumeration protection: the raw "not found" message must not leak through.
                .andExpect(jsonPath("$.results[1].error").value("Not found or not authorized"));

        verify(claims).financeApprove(eq(tenantId), eq(okClaimId), eq(actorId), any());
        verify(claims).financeReject(eq(tenantId), eq(badClaimId), eq(actorId), any());
    }

    @Test
    void bulkActContinuesToLaterItemsAfterAnEarlierItemThrows() throws Exception {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunBulkAction();
        when(claims.financeApprove(eq(tenantId), eq(firstId), eq(actorId), any()))
                .thenThrow(
                        new ApiException(HttpStatus.FORBIDDEN, "You cannot decide your own claim"));
        when(claims.financeApprove(eq(tenantId), eq(secondId), eq(actorId), any()))
                .thenReturn(response(ReimbursementClaimStatus.FINANCE_APPROVED));

        BulkReimbursementDecisionRequest request =
                new BulkReimbursementDecisionRequest(
                        List.of(
                                new BulkReimbursementDecisionItemRequest(
                                        firstId, ApprovalAction.APPROVE, null),
                                new BulkReimbursementDecisionItemRequest(
                                        secondId, ApprovalAction.APPROVE, null)));

        mockMvc.perform(
                        post(BASE + "/finance/bulk-act")
                                .header(HEADER, "key-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1));

        verify(claims).financeApprove(eq(tenantId), eq(secondId), eq(actorId), any());
    }
}
