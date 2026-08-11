package com.ewos.reimbursement.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ewos.employee.application.EmployeeContext;
import com.ewos.reimbursement.api.dto.ReimbursementClaimResponse;
import com.ewos.reimbursement.application.ReimbursementClaimService;
import com.ewos.reimbursement.application.ReimbursementReceiptExtractionService;
import com.ewos.reimbursement.domain.ReimbursementClaimStatus;
import com.ewos.reimbursement.domain.ReimbursementDataSource;
import com.ewos.shared.exception.GlobalExceptionHandler;
import com.ewos.shared.idempotency.IdempotencyService;
import com.ewos.tenancy.application.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Sprint 2 — mirrors {@code EssProfileControllerTest}'s standalone-MockMvc approach: real
 * controller + real {@code GlobalExceptionHandler} against mocked collaborators, exercising HTTP
 * status codes and header/body validation through actual Spring MVC dispatch.
 */
@ExtendWith(MockitoExtension.class)
class ReimbursementSelfServiceControllerTest {

    private static final String BASE = "/api/v1/self-service/reimbursements";
    private static final String HEADER = "Idempotency-Key";

    @Mock ReimbursementClaimService claims;
    @Mock ReimbursementReceiptExtractionService extraction;
    @Mock EmployeeContext employeeContext;
    @Mock TenantContext tenantContext;
    @Mock IdempotencyService idempotency;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReimbursementSelfServiceController controller =
                new ReimbursementSelfServiceController(
                        claims, extraction, employeeContext, tenantContext, idempotency);
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

    private ReimbursementClaimResponse response(ReimbursementClaimStatus status) {
        return new ReimbursementClaimResponse(
                UUID.randomUUID(),
                tenantId,
                UUID.randomUUID(),
                employeeId,
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
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                Instant.now(),
                Instant.now(),
                0L);
    }

    @Test
    void submitReturns400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/submit"))
                .andExpect(status().isBadRequest());

        verify(claims, never()).submit(any(), any(), any());
    }

    @Test
    void submitReturns400WhenIdempotencyKeyHeaderIsBlank() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/submit").header(HEADER, "  "))
                .andExpect(status().isBadRequest());

        verify(claims, never()).submit(any(), any(), any());
    }

    @Test
    void submitReturns404WhenNoEmployeeIsLinkedToTheCaller() throws Exception {
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.empty());

        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/submit").header(HEADER, "key-1"))
                .andExpect(status().isNotFound());

        verify(claims, never()).submit(any(), any(), any());
    }

    @Test
    void submitSucceedsAndScopesToTheCallersOwnEmployeeId() throws Exception {
        UUID claimId = UUID.randomUUID();
        when(tenantContext.homeTenantId()).thenReturn(tenantId);
        when(employeeContext.currentEmployeeId()).thenReturn(Optional.of(employeeId));
        when(tenantContext.currentUserId()).thenReturn(Optional.of(actorId));
        stubIdempotencyToRunTheAction();
        when(claims.submit(tenantId, employeeId, claimId))
                .thenReturn(response(ReimbursementClaimStatus.SUBMITTED));

        mockMvc.perform(post(BASE + "/" + claimId + "/submit").header(HEADER, "key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        verify(claims).submit(tenantId, employeeId, claimId);
    }

    @Test
    void cancelReturns400WhenIdempotencyKeyHeaderIsMissing() throws Exception {
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isBadRequest());

        verify(claims, never()).cancel(any(), any(), any());
    }

    @Test
    void createReturns400WhenAmountIsZero() throws Exception {
        // @Valid rejects the request before the controller method body ever runs, so
        // tenantContext/employeeContext are never consulted for this case.
        String body =
                "{\"categoryId\":\""
                        + UUID.randomUUID()
                        + "\",\"amount\":0,\"claimDate\":\""
                        + LocalDate.now()
                        + "\",\"description\":\"x\"}";

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(claims, never()).createDraft(any(), any(), any());
    }
}
